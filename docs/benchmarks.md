# Benchmarks

PROJECT_BRIEF.md section 7 Phase 5: measured results from the k6 scripts in `load-tests/`
(`mixed-load.js`, `noisy-neighbor.js` - see that directory's README for how to re-run them and
override tenant/VU/duration counts).

## Environment

Single-machine local run, not a production/cloud deployment - numbers below are "does this
design scale and isolate correctly," not an absolute capacity figure:

- Host: Apple M4 Pro (14 CPU), macOS, Docker Desktop (14 CPUs / ~12 GB allotted to the engine).
- App: `docker compose build`'d image (Java 21, Spring Boot 3.3.5), run as a single container,
  default Hikari pool (`maximum-pool-size: 10`), `json-logs` profile active.
- Database: `postgres:16-alpine`, single container, default Compose resource limits (none set).
- k6 (v2.3.0) run on the host, outside Docker, against the app's published port - so results
  include real TCP/loopback overhead, not an in-process benchmark.
- App and Postgres both freshly started (empty database) before each run.

## Mixed read/write load across 50 tenants

`k6 run -e TENANT_COUNT=50 -e VUS=20 -e DURATION=2m load-tests/mixed-load.js`

50 tenants (5 on the `pro` rate-limit tier, 45 on `free` - PROJECT_BRIEF.md section 9's example
split), each seeded with 3 contacts. 20 VUs ramped up over 30s, held for 2 minutes, ramped down -
each iteration picks a random tenant and does a weighted mix of list contacts (50%), get one
contact (20%), create a contact (15%), or create a ticket (15%), with a short think-time sleep.

| Operation | Share of mix | p50 | p90 | p95 | p99 |
|---|---:|---:|---:|---:|---:|
| List contacts (read) | 50% | 4.6 ms | 9.1 ms | 10.3 ms | 12.9 ms |
| Get contact (read) | 20% | 4.1 ms | 8.3 ms | 9.1 ms | 11.8 ms |
| Create contact (write) | 15% | 7.0 ms | 12.5 ms | 14.1 ms | 18.3 ms |
| Create ticket (write) | 15% | 7.3 ms | 13.3 ms | 14.9 ms | 17.6 ms |

(Share is the scripted selection weight in `mixed-load.js`'s `default()`, applied to 4053 total
iterations; k6's summary export doesn't report per-Trend sample counts, only percentiles.)

- **Throughput:** 4303 HTTP requests over the run, ~24.6 req/s sustained at 20 concurrent VUs.
- **Error rate:** 0.00% (0 of 4303 requests failed; all 4153 checks passed).
- **Note on the all-up `http_req_duration` p99 (107 ms):** that aggregate also includes the
  ~150 tenant-onboarding and login calls `setup()` makes before the timed run starts, and both of
  those endpoints run BCrypt password hashing (`PasswordEncoder.encode`/`.matches`), which is
  deliberately slow (tens of ms) - that's what the table above excludes by breaking out the
  steady-state CRUD operations the timed load actually exercises.

## Noisy-neighbor isolation

`k6 run -e NORMAL_TENANT_COUNT=9 -e DURATION=90s -e NOISY_RPS=20 -e NORMAL_RPS=5 load-tests/noisy-neighbor.js`

One tenant (`free` plan, 60 req/min limit) hammered at 20 req/s - 20x its budget. Nine other
`free`-plan tenants got a combined 5 req/s of light read traffic (~0.56 req/s each, comfortably
inside their own budget), for 90 seconds, concurrently with the flood.

| Tenant | Requests (target rate × duration) | Rejected (429) | p50 | p95 | p99 |
|---|---:|---:|---:|---:|---:|
| Noisy (1 tenant) | ~1800 | 1680 (93.3%) | 4.3 ms | 8.6 ms | 11.8 ms |
| Normal (9 tenants, combined) | ~450 | 0 (0%) | 10.7 ms | 15.1 ms | 18.6 ms |

**Done when (PROJECT_BRIEF.md section 7 Phase 5): "the noisy-neighbor test shows other tenants'
p99 stays within target" - met.** The normal tenants' p99 (18.6 ms) is indistinguishable from
their latency in the mixed-load run above (no flood running) and nowhere near the 500 ms target
checked by the script's `normal_tenant_duration` threshold. The noisy tenant, meanwhile, gets
throttled almost immediately: its own `free`-plan bucket absorbs the first ~60 requests, then 429s
for the rest of the minute, over and over.

Confirmed at the metrics layer too (PROJECT_BRIEF.md section 7 Phase 5 item 1 - see
`MetricsConfig`/ADR 0010): querying `/actuator/prometheus` after this run shows
`ratelimit_rejections_total{tenant_id="<noisy tenant>"} 1680` and **no** `ratelimit_rejections`
series at all for any of the 9 normal tenants - the rejection is attributed to, and contained
within, the one tenant causing it.

## Reproducing

```bash
docker compose up -d
# wait for /actuator/health to report UP, then:
k6 run load-tests/mixed-load.js
k6 run load-tests/noisy-neighbor.js
```

See `load-tests/README.md` for environment variable overrides (`BASE_URL`, tenant/VU counts,
durations, admin credentials).
