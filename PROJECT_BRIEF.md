# Project brief: Multi-tenant SaaS backend (mini CRM)

Hand this file to the implementing agent as the first message / project context.

## 1. Why this project exists
- Owner: Sri Kumar, backend engineer (Java, Spring Boot, PostgreSQL, Liquibase, AWS) preparing to apply to Salesforce and Amazon.
- Purpose: a portfolio project that proves understanding of multi-tenancy, data isolation, security, and reliability under load. Interviewers will read the repo, so code quality, tests, README and measured numbers matter as much as features.
- The result will be summarized on a one-page resume with real, measured numbers (latency, throughput, tenant count). Every phase below should produce something measurable or demonstrable.

## 2. Hard rules
- Build from scratch. Do NOT use any employer code, data, schemas, or internal terminology.
- Use synthetic data only.
- Keep business logic small (a CRM: tenants, users, contacts, tickets, comments). Spend effort on isolation, security, tests, and observability.
- Every feature needs tests. Cross-tenant leakage tests are mandatory, not optional.
- Commit in small steps with clear messages. Keep `main` always runnable with `docker compose up`.

## 3. Tech decisions (already made; change only with a written reason in `docs/decisions.md`)
| Area | Choice |
|---|---|
| Language / framework | Java 21, Spring Boot 3.x, Maven |
| Database | PostgreSQL 16 |
| Migrations | Liquibase |
| Isolation model | Shared schema, `tenant_id` on every tenant-owned table, enforced by Postgres Row-Level Security (RLS) |
| Auth | JWT (Spring Security); `tenant_id` and `roles` as claims |
| Tenant resolution | From JWT claim; optional subdomain support later |
| Rate limiting | Per-tenant token bucket (Bucket4j; Redis later if needed) |
| API docs | springdoc-openapi |
| Tests | JUnit 5, Testcontainers (real Postgres), MockMvc / RestAssured |
| Load testing | k6 |
| Local run | Docker Compose (app + Postgres) |
| Observability | Spring Actuator + Micrometer, structured JSON logs with tenant id |

## 4. Architecture in one picture
Client -> API gateway layer (JWT auth, tenant resolution, rate limit) -> application layer (tenant context on every request) -> shared PostgreSQL (every row has `tenant_id`, RLS enforces it).

Request flow:
1. Request arrives with `Authorization: Bearer <jwt>`.
2. A filter validates the JWT, extracts `tenant_id` and roles, and stores them in a request-scoped `TenantContext`.
3. A per-tenant rate limiter checks the bucket for that tenant.
4. When a transaction starts, the app runs `SET LOCAL app.current_tenant = '<tenant_id>'` on the connection.
5. RLS policies on every tenant table filter rows with `tenant_id = current_setting('app.current_tenant')::uuid`.
6. Even if a developer forgets a `WHERE tenant_id = ...`, the database refuses to return or modify another tenant's rows.

Important RLS gotchas (the agent must handle these):
- The app must connect as a non-superuser role that does NOT own the tables. Otherwise RLS is bypassed. Also use `ALTER TABLE ... FORCE ROW LEVEL SECURITY`.
- Use `SET LOCAL` (transaction-scoped), never plain `SET`, because connection pooling reuses connections.
- If no tenant is set, queries must return zero rows (fail closed), not all rows.
- Migrations run as a separate privileged role from the runtime role.

## 5. Data model (initial)
- `tenants` (id, name, plan, status, created_at) -- not RLS-filtered; accessed only by an admin/onboarding path
- `users` (id, tenant_id, email, password_hash, role, created_at); unique on (tenant_id, email)
- `contacts` (id, tenant_id, name, email, phone, created_by, created_at)
- `tickets` (id, tenant_id, contact_id, title, status, priority, assignee_id, created_at)
- `ticket_comments` (id, tenant_id, ticket_id, author_id, body, created_at)
- `audit_log` (id, tenant_id, actor_id, action, entity, entity_id, before/after JSON, created_at)

All tenant tables: composite foreign keys that include `tenant_id` where practical, so a ticket cannot reference another tenant's contact.

## 6. Roles inside a tenant
- `ADMIN`: manage users, view audit log, all CRUD
- `AGENT`: CRUD on contacts, tickets, comments
- `VIEWER`: read-only
Plus a platform-level `PLATFORM_ADMIN` that can create tenants (separate from tenant roles).

## 7. Build plan

### Phase 0 - Repo and tooling (day 1)
1. Create repo `multitenant-crm` with Maven, Java 21, Spring Boot 3 (Web, Security, Data JPA, Validation, Actuator, PostgreSQL driver, Liquibase).
2. Add `docker-compose.yml` with Postgres 16 and the app; add `.env.example`.
3. Add folders: `docs/` (decisions.md, architecture.md), `load-tests/`, `src/...`.
4. Add a CI workflow (GitHub Actions) that runs `mvn verify` with Testcontainers.
5. Write a README skeleton: what it is, how to run, architecture diagram placeholder.
Done when: `docker compose up` starts the app and `/actuator/health` returns UP; CI is green.

### Phase 1 - Schema and tenant isolation (days 2-4)
1. Liquibase changelogs for the tables in section 5, plus two DB roles (`app_migrator`, `app_runtime`).
2. Enable RLS and FORCE RLS on all tenant tables with a policy based on `current_setting('app.current_tenant', true)`.
3. Implement `TenantContext` and a transaction hook (e.g. a `TransactionSynchronization` or a Hibernate/Spring `ConnectionPreparer`) that issues `SET LOCAL app.current_tenant`.
4. Write the isolation test suite with Testcontainers:
   - Tenant A cannot read Tenant B's rows (list, get by id).
   - Tenant A cannot update or delete Tenant B's rows.
   - Tenant A cannot insert a row with Tenant B's `tenant_id`.
   - No tenant set returns zero rows.
   - Cross-tenant foreign key references are rejected.
Done when: all isolation tests pass and are run in CI.

### Phase 2 - Auth and tenant resolution (days 5-6)
1. Tenant onboarding endpoint (platform admin only): creates tenant + first ADMIN user.
2. Login endpoint issuing a JWT with `tenant_id` and `role`.
3. JWT filter populating `TenantContext`; reject requests without a valid tenant.
4. Role checks with method security (`@PreAuthorize`).
Done when: end-to-end test logs in as users of two tenants and proves isolation through the HTTP API.

### Phase 3 - Core CRUD (days 7-9)
1. Contacts, tickets, comments endpoints with pagination, validation, and consistent error format (RFC 7807 problem details).
2. Audit log written for every create/update/delete.
3. OpenAPI docs at `/swagger-ui`.
Done when: Postman/OpenAPI walkthrough works for two tenants side by side.

### Phase 4 - Fairness and reliability (days 10-12)
1. Per-tenant rate limiting by plan (e.g. free 60/min, pro 600/min); return 429 with `Retry-After`.
2. Webhooks: tenants register a URL + event types; deliver with HMAC signature, retries with backoff, and a dead-letter table.
3. Idempotency keys on create endpoints.
Done when: tests show one tenant being throttled does not affect another.

### Phase 5 - Observability and load testing (days 13-14)
1. Micrometer metrics tagged by tenant (cardinality-safe: cap or hash tenant tags), JSON logs with tenant id.
2. k6 scripts: (a) mixed read/write load across 50+ tenants, (b) noisy-neighbor scenario where one tenant floods the API.
3. Record results (p50/p95/p99 latency, RPS, error rate) in `docs/benchmarks.md`.
Done when: benchmark table exists and the noisy-neighbor test shows other tenants' p99 stays within target.

### Phase 6 - Polish for recruiters (day 15)
1. README: architecture diagram, isolation approach, trade-offs vs schema-per-tenant and database-per-tenant, how to run, benchmark table.
2. `docs/decisions.md`: short ADRs for each tech decision.
3. Optional: deploy to AWS (ECS or EC2 + RDS) and add a live link.

## 8. Definition of done (whole project)
- One command runs everything locally.
- Isolation test suite proves no cross-tenant access, enforced at the database level.
- Per-tenant rate limiting and webhooks with retries are tested.
- Benchmarks are measured and documented.
- README explains design choices clearly enough for an interviewer to skim in 3 minutes.

## 9. Resume bullet targets (fill in with real measured numbers later)
- Built a multi-tenant SaaS backend (Spring Boot, PostgreSQL) enforcing tenant isolation with row-level security, verified by an automated cross-tenant test suite.
- Sustained [X] RPS across [N] tenants with p99 under [Y] ms (k6); per-tenant rate limiting kept other tenants' p99 within [Z] ms during a noisy-neighbor test.
- Delivered signed webhooks with retries and dead-letter handling.

## 10. First message to give the implementing agent
"Read PROJECT_BRIEF.md. Start with Phase 0 only. Create the repo structure, Docker Compose, CI, and README skeleton. Stop and show me the result before starting Phase 1. Ask me if any decision in section 3 needs changing."
