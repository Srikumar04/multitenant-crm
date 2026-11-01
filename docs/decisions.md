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

- **Status:** accepted (planned for Phase 2)
- **Decision:** tenant resolution comes from a validated JWT claim, not from the request path
  or subdomain, to start.
- **Why:** simplest to implement and test; subdomain-based resolution can be layered on later
  without changing the isolation model.

(More entries added as later phases land: rate limiting choice, webhook delivery model, etc.)
