# Load tests

k6 scripts for PROJECT_BRIEF.md section 7 Phase 5. Both provision their own tenants against the
running app (no fixtures needed) via `lib/provision.js`.

## Prerequisites

- The app running and reachable (`docker compose up`, or any `BASE_URL`).
- [k6](https://k6.io/docs/get-started/installation/) installed locally (`brew install k6`).

## Scripts

- `mixed-load.js` - mixed read/write load across 50+ tenants.
- `noisy-neighbor.js` - one tenant flooding the API while others see light traffic; proves
  per-tenant rate limiting isolates them.

## Running

```bash
k6 run load-tests/mixed-load.js \
  --summary-export=load-tests/results/mixed-load-summary.json

k6 run load-tests/noisy-neighbor.js \
  --summary-export=load-tests/results/noisy-neighbor-summary.json
```

Useful overrides (both scripts): `-e BASE_URL=http://localhost:8080`, `-e ADMIN_USER=...`,
`-e ADMIN_PASSWORD=...` (platform admin credentials - see `.env`/`application.yml` defaults),
`-e DURATION=2m`. `mixed-load.js` also takes `-e TENANT_COUNT=50 -e VUS=20`; `noisy-neighbor.js`
takes `-e NORMAL_TENANT_COUNT=9 -e NOISY_RPS=20 -e NORMAL_RPS=5`.

Results referenced in `docs/benchmarks.md` were run with the app in its `json-logs` Docker
Compose configuration (`docker compose up -d`) and k6 outside the container, hitting
`localhost:8080`.
