# Frontend

A plain HTML/CSS/JS admin console for the backend — no build step, no framework, no npm. Static
files served as-is (nginx in Docker Compose, or anything else that serves static files).

## What it does

- **Onboard a tenant** (platform-admin credentials) and **log in** as a tenant user.
- **Contacts** and **tickets** CRUD, with pagination. Tickets open a dialog for comments.
- **Webhook subscriptions** (ADMIN only): create (pick event types, get a one-time secret),
  list, delete, and a read-only view of the dead-letter table (deliveries that exhausted retries).
- **Audit log** (ADMIN only): paginated, with a before/after JSON diff per entry.
- A persistent header shows the current tenant, plan, and rate limit; a banner appears with a
  live countdown whenever a request comes back `429`.
- Role-aware UI: write actions and the Webhooks/Audit Log tabs are hidden for `VIEWER`/`AGENT`
  where the backend would reject them anyway (`@PreAuthorize` is still the real enforcement -
  this is just not showing a button that would 403).

## Running it

```bash
docker compose up
```

Compose serves this folder via nginx at **http://localhost:8081**, with `CORS_ALLOWED_ORIGINS`
on the `app` service already set to match it (see `docker-compose.yml`,
`SecurityConfig.corsConfigurationSource`, and `docs/decisions.md` ADR 0011).

To run it standalone against an API elsewhere, serve this folder with any static file server
(`python3 -m http.server 8081` works) and set the **API base URL** field on the login screen —
it's remembered in `localStorage`. The target API needs `http://<your-origin>` in its
`CORS_ALLOWED_ORIGINS`.

## Notable choices

- The JWT is kept in `sessionStorage`, not `localStorage` — on purpose. Each browser tab gets its
  own session, so you can log into two different tenants in two tabs side by side and watch
  tenant isolation live (the entire point of this backend) instead of just reading it in a test
  report.
- Every `POST` gets a fresh `Idempotency-Key` header automatically (`crypto.randomUUID()`),
  exercising the backend's idempotency-key feature (ADR 0008) the way a real client should.
- Role checks here are cosmetic. The backend's `@PreAuthorize` annotations are the actual
  security boundary; this UI just avoids showing a VIEWER a button that would come back `403`.
