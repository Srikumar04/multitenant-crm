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
