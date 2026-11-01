# Architecture

See `PROJECT_BRIEF.md` section 4 for the original design; this reflects what's actually
implemented through Phase 5.

```
                              Client
                                │  Authorization: Bearer <jwt>
                                ▼
 ┌───────────────────────────────────────────────────────────────────────┐
 │ API gateway layer (servlet filters, in order)                         │
 │ 1. JwtAuthenticationFilter  - validates JWT, sets TenantContext       │
 │ 2. IdempotencyFilter        - replays a stored response on hit        │
 │ 3. RateLimitFilter          - per-tenant token bucket                 │
 └───────────────────────────────────────────────────────────────────────┘
                                  ▼
 ┌───────────────────────────────────────────────────────────────────────┐
 │ Application layer                                                     │
 │ Controller -> Service (TransactionTemplate) -> audit log / webhooks   │
 └───────────────────────────────────────────────────────────────────────┘
                                  │  SET LOCAL app.current_tenant = '<id>'
                                  ▼
 ┌───────────────────────────────────────────────────────────────────────┐
 │ PostgreSQL - shared tables, every tenant row has tenant_id            │
 │ Row-Level Security: tenant_id = current_setting('app.current_tenant') │
 │ FORCE RLS + a non-owner runtime role: no tenant set means zero rows,  │
 │ not an error and not all rows (fail closed).                          │
 └───────────────────────────────────────────────────────────────────────┘
```

## Request flow

1. Request arrives with `Authorization: Bearer <jwt>`.
2. `JwtAuthenticationFilter` validates the JWT, extracts `tenant_id`, `user_id`, and `role`, and
   populates three things for the rest of the request: `TenantContext` (a `ThreadLocal`, read by
   `TenantAwareJpaTransactionManager`), the Spring Security `Authentication` (for `@PreAuthorize`
   role checks), and MDC's `tenantId` key (so every log line for this request - in either the
   default or `json-logs` format, see ADR 0009 - carries it). A missing/invalid token leaves no
   `Authentication` set, which `SecurityConfig`'s `.anyRequest().authenticated()` turns into a 401.
3. `IdempotencyFilter` checks for a stored response keyed by (tenant, `Idempotency-Key` header,
   method, path) on `POST` requests; a hit replays the original response without re-running the
   handler.
4. `RateLimitFilter` checks a per-tenant Bucket4j token bucket (sized by the tenant's plan); an
   exhausted bucket returns `429` with `Retry-After` instead of reaching the controller.
5. The controller delegates to a service, which wraps its repository calls in an explicit
   `TransactionTemplate.execute(...)` (ADR 0004). `TenantAwareJpaTransactionManager` runs
   `SET LOCAL app.current_tenant = '<tenant_id>'` on the connection in `doBegin()` -
   transaction-scoped, which matters because the connection comes from a pool and a plain `SET`
   would leak the setting to whichever request reuses that connection next.
6. Postgres RLS policies on every tenant table filter rows with
   `tenant_id = current_setting('app.current_tenant')::uuid`. Even if a developer forgets a
   `WHERE tenant_id = ...`, the database refuses to return or modify another tenant's rows; with
   no tenant set, every query returns zero rows (fail closed), never all rows.
7. On commit, `ContactService`/`TicketService` dispatch any registered webhooks `afterCommit`
   (ADR 0007), off the request thread, so a slow or malicious receiver can't degrade the API
   response for a write that already succeeded.

The app connects as `app_runtime`, a non-superuser role that does not own the tables (RLS
would otherwise be bypassed), with `FORCE ROW LEVEL SECURITY` set on every tenant table.
Migrations run as a separate, more privileged `app_migrator` role.

## Observability

- Every HTTP request is recorded as an `http.server.requests` Micrometer metric, tagged with
  `tenant_id` (cardinality-capped - ADR 0010) and exposed at `/actuator/prometheus`.
- `ratelimit.rejections` is a tenant-tagged counter incremented on every `429`, so a noisy
  tenant's throttling is visible per-tenant, not just in aggregate (see `docs/benchmarks.md`'s
  noisy-neighbor results).
- Logs carry `tenantId` via MDC in both the default (human-readable) and `json-logs` (one JSON
  object per line, active in Docker Compose) formats - ADR 0009.

See `docs/decisions.md` for the full reasoning behind each of these choices, and
`docs/benchmarks.md` for measured load test results.
