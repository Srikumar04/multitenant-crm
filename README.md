# multitenant-crm

A multi-tenant SaaS backend (mini CRM) built to demonstrate tenant data isolation, security,
and reliability under load — a portfolio project, not a product. See `PROJECT_BRIEF.md` for the
full spec, [`docs/decisions.md`](docs/decisions.md) for the reasoning behind every tech choice,
and [`docs/benchmarks.md`](docs/benchmarks.md) for measured load test results.

**Status:** feature-complete through `PROJECT_BRIEF.md` section 7 Phase 5 — schema & RLS
isolation, JWT auth, CRUD with audit logging, per-tenant rate limiting, signed webhooks with
retries, idempotency keys, tenant-tagged metrics, JSON logs, and measured k6 benchmarks.

## What this is

A CRM (tenants, users, contacts, tickets, comments) where tenant data lives in shared
Postgres tables, isolated by `tenant_id` and enforced with Postgres **Row-Level Security** —
not just application-level filtering. Business logic is deliberately small; the effort went
into isolation, security, tests, and observability.

- **Tenant isolation at the database level**, not just in application code — proven by an
  automated cross-tenant leakage test suite (list, get, update, delete, insert, cross-tenant
  foreign keys, and the no-tenant-set case all fail closed).
- **JWT auth** with `tenant_id` + `role` claims; `ADMIN` / `AGENT` / `VIEWER` roles enforced with
  `@PreAuthorize`.
- **Contacts, tickets, and ticket comments** CRUD — pagination, bean validation, RFC 7807
  problem-detail errors, OpenAPI docs, and an audit log entry for every create/update/delete.
- **Per-tenant rate limiting** (free/pro token-bucket tiers) returning `429` + `Retry-After`,
  isolated so one tenant's flood can't degrade another's (measured in benchmarks below).
- **Signed webhooks** (HMAC-SHA256, GitHub/Stripe-style) with retries and a dead-letter table.
- **Idempotency keys** on create endpoints, scoped to (tenant, key, method, path).
- **Tenant-tagged, cardinality-capped Micrometer metrics** and JSON logs carrying `tenantId`.

## Architecture

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

See [`docs/architecture.md`](docs/architecture.md) for the full request-by-request walkthrough.

### Isolation model and the alternatives

| Approach | Isolation strength | Operational cost | Migration complexity |
|---|---|---|---|
| **Shared schema + RLS (this project)** | Strong — enforced by Postgres on every query, fails closed if a tenant filter is ever forgotten | Low — one database to run, scale, and back up | Low — one Liquibase changelog, run once |
| Schema-per-tenant | Stronger — physical separation within one database | Medium — N schemas to migrate and monitor | Higher — migration runs once per schema, drift risk |
| Database-per-tenant | Strongest — full blast-radius isolation | High — N databases to provision, monitor, back up | Highest — N migration runs, N connection pools |

Shared schema + RLS wins at this project's scale: it's the cheapest to run, and RLS turns the
most common human mistake (a forgotten `WHERE tenant_id = ...`) into a database-level failure
closed to zero rows, instead of a silent cross-tenant data leak. The other two options buy
stronger blast-radius isolation — worth it for a real compliance requirement — at migration and
operational cost that isn't justified here. Full reasoning in
[`docs/decisions.md`](docs/decisions.md) ADR 0001.

## Running locally

```bash
docker compose up
```

This starts Postgres 16 and the app (JSON logs via the `json-logs` Spring profile — see ADR
0009). Once up:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

- API docs: http://localhost:8080/swagger-ui.html
- Tenant-tagged Prometheus metrics: http://localhost:8080/actuator/prometheus

## Running tests

```bash
mvn verify
```

27 tests, including an isolation suite that runs against a real Postgres instance via
Testcontainers — no mocked database, so the RLS policies are genuinely exercised.

## Load testing

```bash
k6 run load-tests/mixed-load.js
k6 run load-tests/noisy-neighbor.js
```

See [`load-tests/README.md`](load-tests/README.md) for options and
[`docs/benchmarks.md`](docs/benchmarks.md) for the full write-up. Headline numbers from the
measured run (single-machine, see benchmarks doc for environment details):

| Test | Result |
|---|---|
| Mixed read/write load, 50 tenants, 20 VUs | 0.00% errors, ~25 req/s, p99 12–18 ms on CRUD ops |
| Noisy-neighbor (1 flooding tenant vs. 9 normal) | Flooded tenant: 93.3% throttled (429). Other 9 tenants: 0% errors, p99 18.6 ms — unaffected |

## Design decisions

See [`docs/decisions.md`](docs/decisions.md) for short ADRs on isolation model, auth, rate
limiting, webhooks, idempotency, logging, and metrics — including trade-offs against
schema-per-tenant and database-per-tenant.
