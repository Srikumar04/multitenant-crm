# multitenant-crm

A multi-tenant SaaS backend (mini CRM) built to demonstrate tenant data isolation, security,
and reliability under load — not a product. See `PROJECT_BRIEF.md` for the full spec and
`docs/decisions.md` for the reasoning behind each tech choice.

**Status:** Phase 0 (repo scaffold). Isolation, auth, CRUD, rate limiting, webhooks, and
benchmarks land in later phases — see `PROJECT_BRIEF.md` section 7 for the full plan.

## What this is

A CRM (tenants, users, contacts, tickets, comments) where tenant data lives in shared
Postgres tables, isolated by `tenant_id` and enforced with Postgres Row-Level Security —
not just application-level filtering. See [`docs/architecture.md`](docs/architecture.md)
for the request flow and why RLS was chosen over schema-per-tenant or database-per-tenant.

## Running locally

```bash
docker compose up
```

This starts Postgres 16 and the app. Once up:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

API docs (once endpoints exist, from Phase 3 on): http://localhost:8080/swagger-ui.html

## Running tests

```bash
mvn verify
```

Integration tests use Testcontainers to run against a real Postgres instance — no mocked
database.

## Architecture

See [`docs/architecture.md`](docs/architecture.md).

## Benchmarks

See [`docs/benchmarks.md`](docs/benchmarks.md) for measured k6 load test results (p50/p95/p99
latency, RPS, error rate, and the noisy-neighbor isolation test) — scripts in `load-tests/`.

## Design decisions

See [`docs/decisions.md`](docs/decisions.md) for short ADRs on isolation model, auth, and
other tech choices, including trade-offs against schema-per-tenant and database-per-tenant.
