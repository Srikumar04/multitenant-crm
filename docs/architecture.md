# Architecture

> Diagram placeholder — a real diagram goes here once Phase 1/2 land. See
> `PROJECT_BRIEF.md` section 4 for the request flow this implements.

```
Client -> API gateway layer (JWT auth, tenant resolution, rate limit)
       -> application layer (tenant context on every request)
       -> shared PostgreSQL (every row has tenant_id, RLS enforces it)
```

Request flow:
1. Request arrives with `Authorization: Bearer <jwt>`.
2. A filter validates the JWT, extracts `tenant_id` and `role`, and stores them in a
   request-scoped `TenantContext`.
3. A per-tenant rate limiter checks the token bucket for that tenant.
4. When a transaction starts, the app runs `SET LOCAL app.current_tenant = '<tenant_id>'`
   on the connection (transaction-scoped — safe under connection pooling).
5. RLS policies on every tenant table filter rows with
   `tenant_id = current_setting('app.current_tenant')::uuid`.
6. Even if a developer forgets a `WHERE tenant_id = ...`, the database refuses to return or
   modify another tenant's rows. No tenant set -> zero rows (fail closed).

The app connects as `app_runtime`, a non-superuser role that does not own the tables (RLS
would otherwise be bypassed), with `FORCE ROW LEVEL SECURITY` set on every tenant table.
Migrations run as a separate, more privileged `app_migrator` role.
