/**
 * PROJECT_BRIEF.md section 7 Phase 5 item 2(b): one tenant floods the API; other tenants' latency
 * should stay unaffected because rate limiting (Phase 4) is per-tenant, not shared.
 *
 * Two scenarios run concurrently for the same duration:
 *  - `noisy_neighbor`: one "noisy" free-plan tenant, driven well past its 60 req/min bucket
 *    (PROJECT_BRIEF.md section 9) - expect a steady stream of 429s for this tenant only.
 *  - `normal_traffic`: NORMAL_TENANT_COUNT other free-plan tenants doing light, well-within-limit
 *    reads. `normal_tenant_duration` (see docs/benchmarks.md for the measured numbers) is the
 *    metric that proves isolation: it should look the same whether or not the noisy scenario is
 *    running.
 *
 * Run:
 *   k6 run load-tests/noisy-neighbor.js --summary-export=load-tests/results/noisy-neighbor-summary.json
 */
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { BASE_URL, provisionTenant, seedContact, authHeaders } from './lib/provision.js';

const NORMAL_TENANT_COUNT = parseInt(__ENV.NORMAL_TENANT_COUNT || '9', 10);
const DURATION = __ENV.DURATION || '90s';
// Free plan is 60 req/min = 1 req/s; driven well past that to guarantee throttling.
const NOISY_RPS = parseInt(__ENV.NOISY_RPS || '20', 10);
// Spread across NORMAL_TENANT_COUNT tenants, this should stay comfortably under each tenant's
// own 1 req/s budget (5 / 9 =~ 0.56 req/s per tenant).
const NORMAL_RPS = parseInt(__ENV.NORMAL_RPS || '5', 10);

const normalTenantDuration = new Trend('normal_tenant_duration');
const noisyTenantDuration = new Trend('noisy_tenant_duration');
const noisyTenant429s = new Counter('noisy_tenant_429s');
const normalTenant429s = new Counter('normal_tenant_429s');

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    normal_traffic: {
      executor: 'constant-arrival-rate',
      rate: NORMAL_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 20,
      maxVUs: 50,
      exec: 'normalTraffic',
    },
    noisy_neighbor: {
      executor: 'constant-arrival-rate',
      rate: NOISY_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: 20,
      maxVUs: 50,
      exec: 'noisyTraffic',
      startTime: '5s',
    },
  },
  setupTimeout: '120s',
  thresholds: {
    // Documents the target from PROJECT_BRIEF.md section 7 Phase 5's "done when" line; does not
    // abort the run (see docs/benchmarks.md for the measured number this was checked against).
    normal_tenant_duration: [{ threshold: 'p(99)<500', abortOnFail: false }],
  },
};

export function setup() {
  const noisy = provisionTenant('noisy', 'free');
  const normals = [];
  for (let i = 0; i < NORMAL_TENANT_COUNT; i++) {
    const tenant = provisionTenant(`normal-${i}`, 'free');
    tenant.contactId = seedContact(tenant.token, `normal-${i}`);
    normals.push(tenant);
  }
  return { noisy, normals };
}

export function noisyTraffic(data) {
  const res = http.get(`${BASE_URL}/api/contacts`, {
    ...authHeaders(data.noisy.token),
    tags: { name: 'noisy_list_contacts' },
  });
  noisyTenantDuration.add(res.timings.duration);
  if (res.status === 429) noisyTenant429s.add(1);
  check(res, { 'noisy tenant: 200 or 429': (r) => r.status === 200 || r.status === 429 });
}

export function normalTraffic(data) {
  const tenant = data.normals[Math.floor(Math.random() * data.normals.length)];
  const res = http.get(`${BASE_URL}/api/contacts`, {
    ...authHeaders(tenant.token),
    tags: { name: 'normal_list_contacts' },
  });
  normalTenantDuration.add(res.timings.duration);
  if (res.status === 429) normalTenant429s.add(1);
  check(res, { 'normal tenant: 200': (r) => r.status === 200 });
}
