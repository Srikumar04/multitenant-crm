# Architecture decisions

Short ADRs for each tech decision in `PROJECT_BRIEF.md` section 3. One entry per decision,
added as it's implemented or revisited.

## 0001 - Shared schema with Postgres RLS over schema-per-tenant / database-per-tenant

- **Status:** accepted (Phase 0)
- **Context:** need to isolate tenant data; three standard options are shared tables with a
  `tenant_id` column, schema-per-tenant, or database-per-tenant.
- **Decision:** shared tables, `tenant_id` on every tenant-owned table, enforced at the
  database level with Postgres Row-Level Security (not just application-level filtering).
- **Why:** cheapest to run and easiest to add tenants to; RLS makes a forgotten
  `WHERE tenant_id = ...` fail closed instead of leaking data. Schema- or database-per-tenant
  give stronger blast-radius isolation and easier single-tenant restore, at the cost of much
  more complex migrations and operational overhead — not worth it at this scale.
- **Revisit if:** a tenant needs guaranteed physical data separation (e.g. a compliance
  requirement), or migrations become the bottleneck at high tenant counts.

## 0002 - JWT-based auth with tenant_id as a claim

- **Status:** accepted (Phase 2)
- **Decision:** tenant resolution comes from a validated JWT claim, not from the request path
  or subdomain, to start.
- **Why:** simplest to implement and test; subdomain-based resolution can be layered on later
  without changing the isolation model.

## 0003 - Login request names the tenant explicitly

- **Status:** accepted (Phase 2)
- **Decision:** `POST /api/auth/login` takes `{tenantId, email, password}` rather than resolving
  the tenant from a subdomain.
- **Why:** subdomain-based resolution is explicitly a later enhancement (ADR 0002), so at login
  time - before any JWT exists - the tenant has to come from somewhere. The login page is
  assumed to be tenant-specific (the user is already "on" tenant A's login screen), matching how
  the onboarding flow hands back a `tenantId` the frontend would store. The alternative (look up
  a user by email alone across all tenants) would require a privileged cross-tenant query path
  that bypasses RLS before any identity is established, which is worse: it reintroduces exactly
  the kind of unscoped query this project exists to avoid.
- **Revisit if:** subdomain-based tenant resolution is added later - the login page would then
  already know its tenant, and this field could become optional/derived.

## 0004 - Tenant-scoped repository calls are wrapped in an explicit transaction

- **Status:** accepted (Phase 2)
- **Context:** `TenantAwareJpaTransactionManager` (Phase 1) sets the RLS session var in
  `doBegin()`. Calling a derived Spring Data query method directly (e.g.
  `userRepository.findByTenantIdAndEmail(...)`) without an enclosing transaction silently found
  nothing during manual testing: inherited `CrudRepository` methods like `findAll()` reliably
  trigger their own transaction through our custom manager, but a derived query method declared
  only on the repository interface did not reproduce that behavior reliably in testing - the
  session var was never set, so RLS fell back to its fail-closed default of zero rows.
- **Decision:** every tenant-scoped service wraps its repository calls in an explicit
  `TransactionTemplate.execute(...)` (see `TenantOnboardingService`, `AuthService`), the same
  pattern the test suite's `asTenant()` helper already uses, instead of relying on whichever
  transaction Spring Data happens to open implicitly for a given method.
- **Why:** this makes the RLS dependency explicit and visible at the call site rather than
  resting on an implicit, method-signature-dependent default that silently fails closed (as zero
  rows, not an error) if it doesn't fire - the worst kind of bug for a system whose entire job is
  tenant isolation.

## 0005 - Security error responses write the body directly, never via response.sendError()

- **Status:** accepted (Phase 3)
- **Context:** found while testing that a VIEWER denied by `@PreAuthorize` got back 401 instead
  of 403. Spring Security's default `AccessDeniedHandlerImpl` calls `response.sendError(403)`,
  which triggers the servlet container's `/error` forward - under
  `SessionCreationPolicy.STATELESS` that forward re-enters the same security filter chain as a
  fresh, unauthenticated request, which the `authenticationEntryPoint` then challenges with 401,
  silently overwriting the 403 before the client ever sees it.
- **Decision:** `SecurityConfig` supplies custom `accessDeniedHandler`/`authenticationEntryPoint`
  beans that write an RFC 7807 `ProblemDetail` body and set the status directly on the response,
  never calling `sendError()`.
- **Why:** this is the correct general fix for any stateless Spring Security setup, not a
  one-off workaround - and it has the added benefit of making authn/authz failures return the
  same `application/problem+json` shape as every other error in the API (section 7 Phase 3's
  "consistent error format" goal).

## 0006 - Rate limits are plan-based token buckets, in-process, keyed by tenant id

- **Status:** accepted (Phase 4)
- **Decision:** `RateLimitService` keeps one Bucket4j token bucket per tenant in a plain
  `ConcurrentHashMap`, sized from the tenant's `plan` column (free: 60 req/min, pro: 600 req/min -
  PROJECT_BRIEF.md section 9's example numbers, configurable via `app.rate-limit.*` /
  `RATE_LIMIT_FREE` / `RATE_LIMIT_PRO`). `RateLimitFilter` runs immediately after
  `JwtAuthenticationFilter` (TenantContext is already populated) and returns 429 with a
  `Retry-After` header and a problem+json body when a bucket is exhausted.
- **Why in-process, not Redis:** PROJECT_BRIEF.md section 3 explicitly defers Redis to "later, if
  needed." A single-instance deployment has no cross-instance state to share, so an in-memory map
  is strictly simpler and has nothing to get out of sync.
- **Revisit if:** the app runs as more than one instance - an in-memory bucket per instance would
  let a tenant get roughly `limit * instance_count` requests through, defeating the limit.

## 0007 - Webhook delivery dispatches after commit, off the request thread

- **Status:** accepted (Phase 4)
- **Decision:** `ContactService`/`TicketService` register a `TransactionSynchronization`
  (`afterCommit`) inside their existing `TransactionTemplate.execute()` block that calls
  `WebhookDeliveryService`'s `@Async` delivery method, passing tenant id, subscription id and the
  already-serialized payload as plain parameters rather than letting the async method read
  `TenantContext` (a `ThreadLocal` that isn't populated on the executor's thread). The async
  method sets `TenantContext` itself, only for the DB calls it actually needs (looking up
  subscriptions, writing a dead-letter row).
- **Why:** a webhook receiver is someone else's server - it can be slow, down, or actively
  malicious. Delivering synchronously on the request thread would let a bad receiver degrade or
  fail the API response for an operation that already succeeded. Dispatching only `afterCommit`
  (not inline in the service method) also avoids firing a webhook for a change that gets rolled
  back later in the same transaction.
- **Gotcha found while building this:** looking up subscriptions inside the `afterCommit`
  callback using the shared, default-propagation `TransactionTemplate` silently returned zero
  rows, even for a subscription that definitely existed. `afterCommit()` runs while the
  triggering transaction's resources are still bound to the thread (full cleanup happens
  slightly later), so a `PROPAGATION_REQUIRED` call inside it just joins that already-committed,
  about-to-be-discarded transaction instead of starting a fresh one. Fixed by giving
  `WebhookDispatchService` its own `TransactionTemplate` built with
  `PROPAGATION_REQUIRES_NEW` for that specific lookup.
- **Secret/signature scheme:** each subscription gets a server-generated random secret (shown
  once, at creation). Every delivery is signed `HMAC-SHA256(secret, rawJsonBody)` and sent as an
  `X-Webhook-Signature: sha256=<hex>` header, the same convention GitHub/Stripe use, so a receiver
  can verify authenticity without a shared-nothing allowlist.
- **Retries:** a fixed attempt count with configurable backoff (`app.webhooks.max-attempts`,
  `app.webhooks.backoff-ms`), exhausting into a `webhook_dead_letters` row with the last error -
  per PROJECT_BRIEF.md section 7 Phase 4. Comments don't fire webhooks (contacts and tickets do);
  the brief doesn't enumerate exact event types, and extending to comments later is a small,
  isolated addition once there's a real subscriber asking for it.

## 0008 - Idempotency keys are scoped to (tenant, key, method, path)

- **Status:** accepted (Phase 4)
- **Decision:** `IdempotencyFilter` runs after `JwtAuthenticationFilter`, and for a POST request
  carrying an `Idempotency-Key` header, looks up a stored response by tenant id + key + HTTP
  method + path. A hit replays the exact stored status and body without invoking the handler
  again; a miss runs the request normally (via `ContentCachingResponseWrapper`) and stores
  whatever response came back - including an error - before returning it.
- **Why scope by method+path too, not just the key:** a client is expected to generate a fresh
  key per logical operation, but scoping narrowly costs nothing and prevents an accidental key
  collision between two different endpoints from one tenant silently replaying the wrong
  resource's response.
- **Why cache failures too:** standard idempotency-key semantics (as in the Stripe/GitHub
  convention this mirrors) are "replay whatever happened the first time," not "retry until
  success" - the caller asked a specific operation to happen once, and a retried request should
  see that one outcome, success or failure.

## 0009 - JSON logs are a Spring profile, not the default

- **Status:** accepted (Phase 5)
- **Decision:** `logback-spring.xml` keeps the existing human-readable console pattern as the
  default, and adds a `json-logs` Spring profile (via `logstash-logback-encoder`) that switches
  the same console appender to one JSON object per line, with `tenantId` included from MDC.
  Docker Compose's `app` service activates `json-logs`. `JwtAuthenticationFilter` puts the
  resolved tenant id into MDC (`MDC.put("tenantId", ...)`, cleared in the same `finally` that
  clears `TenantContext`) so every log line for a request - in either format - carries it, per
  PROJECT_BRIEF.md section 7 Phase 5 item 1.
- **Why a profile instead of switching the default:** `mvn test`/local `java -jar` runs are read
  by a human in a terminal, where the existing `[tenant=...]` pattern is more scannable; JSON
  earns its keep once logs are going somewhere that parses them (Docker Compose, a real
  deployment), which is exactly where `json-logs` gets activated.

## 0010 - Tenant-tagged metrics, cardinality-capped; `/actuator/prometheus` left unauthenticated

- **Status:** accepted (Phase 5)
- **Context:** Spring Boot 3's Micrometer Observation API replaced Boot 2's `WebMvcTagsProvider`
  hook - `MetricsConfig` instead supplies a `ServerRequestObservationConvention` bean that adds a
  `tenant_id` tag to `http.server.requests`. Tenants are created at runtime with no upper bound
  (onboarding is just an admin API call), so tagging every meter by raw tenant id is an
  unbounded-cardinality Prometheus time series waiting to happen.
- **Decision:** a `MeterFilter.maximumAllowableTags(..., 300, MeterFilter.deny())` caps
  `http.server.requests` (and the `ratelimit.rejections` counter `RateLimitFilter` increments on
  every 429) at 300 distinct `tenant_id` values; requests from tenant 301+ still work normally,
  they just stop growing that particular metric's cardinality. `/actuator/prometheus` was added
  to `SecurityConfig`'s `permitAll` list (alongside `/actuator/health`) so it's actually
  scrapeable - everything else under `/actuator` still requires authentication.
- **Gotcha found while building this:** the tag can't be read from `TenantContext` at the point
  the observation convention runs. `ServerHttpObservationFilter` (which records
  `http.server.requests`) wraps the *entire* Spring Security filter chain and computes its tags
  in its own `finally`, which runs *after* `JwtAuthenticationFilter`'s `finally` has already
  cleared the `TenantContext` ThreadLocal on the way out. `JwtAuthenticationFilter` now also sets
  a plain request attribute (`JwtAuthenticationFilter.TENANT_ID_REQUEST_ATTRIBUTE`), which the
  observation convention reads instead - it lives as long as the request object, not the thread,
  so the ordering doesn't matter.
- **Why permit `/actuator/prometheus` unauthenticated:** there's no separate scrape-credential
  concept in this project, and the alternative (platform-admin basic auth on every scrape) just
  pushes the real problem - restricting *who can reach* the endpoint - onto a credential that
  would have to be baked into a Prometheus config file instead. The endpoint only ever exposes
  tenant *ids* and request counts/latencies, not tenant data.
- **Revisit if:** this ever runs somewhere actually reachable from outside a trusted network -
  the honest fix then is restricting the path at the network/ingress level (or a dedicated scrape
  token), not application-level `permitAll`.

## 0011 - A static HTML/CSS/JS frontend, CORS opened for its origin, plus the read endpoints it needed

- **Status:** accepted
- **Decision:** `frontend/` is plain HTML/CSS/JS - no npm, no build step, no framework - served
  by its own `nginx:alpine` container in Docker Compose on port 8081. `SecurityConfig` gains a
  `CorsConfigurationSource` bean (`app.cors.allowed-origins`, default `http://localhost:8081`)
  so a browser on that origin can call the API on 8080. No cookies are used anywhere in this app
  (JWT and platform-admin auth are both plain headers), so CORS credentials stay off, which keeps
  the allowed-origins list simple instead of needing exact-origin echo-back logic.
- **Why no framework:** this is a demo console for the backend's features (PROJECT_BRIEF.md's
  actual subject), not a product with its own frontend architecture - a build toolchain would be
  pure overhead for four pages and some fetch calls.
- **Session storage, not local storage:** the JWT lives in `sessionStorage`, scoped per browser
  tab. That turns "open two tabs, log into two tenants" into a live, visual isolation demo -
  exactly the thing the automated test suite otherwise only proves in a report.
- **New read endpoints, added because the frontend needed them, not speculatively:**
  - `GET /api/tenants/me` - a tenant user's own name/plan/status/rate-limit, for the dashboard
    header. Tenant lookups still go through `TenantRepository` directly (`TenantSelfService`),
    the same pattern `RateLimitService` already uses, since `tenants` has no RLS (PROJECT_BRIEF.md
    section 5) and is scoped by `TenantContext` manually instead.
  - `GET /api/audit-log` (ADMIN) - PROJECT_BRIEF.md section 6 already specified "ADMIN: view
    audit log" as a role capability, but no HTTP endpoint existed for it before this; `AuditLog`
    rows were being written (Phase 3) but never read back through the API.
  - `GET /api/webhooks` (ADMIN, via a `WebhookSubscriptionSummary` that has no `secret` field at
    all - not just one that omits it at serialization time) and `DELETE /api/webhooks/{id}`
    (ADMIN) - only `POST` existed before; there was no way to see or remove a subscription once
    created.
  - `GET /api/webhooks/dead-letters` (ADMIN) - the `webhook_dead_letters` table (Phase 4) had no
    read path either; this makes the retry/dead-letter behavior (ADR 0007) actually observable
    instead of only verifiable by querying the database directly.
