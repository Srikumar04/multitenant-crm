/**
 * PROJECT_BRIEF.md section 7 Phase 5 item 2(a): mixed read/write load across 50+ tenants.
 *
 * setup() onboards TENANT_COUNT tenants (platform-admin API), logs each in, and seeds a few
 * contacts per tenant. The default function then picks a random tenant per iteration - spreading
 * load across all of them rather than pinning one tenant per VU - and does a weighted mix of
 * contact/ticket reads and writes.
 *
 * Run (against docker compose's app, exposed on localhost:8080):
 *   k6 run load-tests/mixed-load.js --summary-export=load-tests/results/mixed-load-summary.json
 *
 * Override tenant/VU/duration counts, e.g. for a quick smoke run:
 *   k6 run -e TENANT_COUNT=10 -e VUS=5 -e DURATION=30s load-tests/mixed-load.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, provisionTenant, seedContact, authHeaders } from './lib/provision.js';

const TENANT_COUNT = parseInt(__ENV.TENANT_COUNT || '50', 10);
const VUS = parseInt(__ENV.VUS || '20', 10);
const DURATION = __ENV.DURATION || '2m';
const CONTACTS_PER_TENANT = 3;

const listContactsDuration = new Trend('list_contacts_duration');
const getContactDuration = new Trend('get_contact_duration');
const createContactDuration = new Trend('create_contact_duration');
const createTicketDuration = new Trend('create_ticket_duration');

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    mixed_crud: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: VUS },
        { duration: DURATION, target: VUS },
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  setupTimeout: '180s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  const tenants = [];
  for (let i = 0; i < TENANT_COUNT; i++) {
    // Roughly 1 in 10 tenants on the higher "pro" rate-limit tier, matching the kind of mix a
    // real tenant base would have (PROJECT_BRIEF.md section 9's free/pro example).
    const plan = i % 10 === 0 ? 'pro' : 'free';
    const tenant = provisionTenant(`mixed-${i}`, plan);
    const contactIds = [];
    for (let c = 0; c < CONTACTS_PER_TENANT; c++) {
      const id = seedContact(tenant.token, `${i}-${c}`);
      if (id) contactIds.push(id);
    }
    tenants.push({ ...tenant, contactIds });
  }
  return { tenants };
}

export default function (data) {
  const tenant = data.tenants[Math.floor(Math.random() * data.tenants.length)];
  const headers = authHeaders(tenant.token);
  const roll = Math.random();

  if (roll < 0.5) {
    const res = http.get(`${BASE_URL}/api/contacts?size=20`, { ...headers, tags: { name: 'list_contacts' } });
    listContactsDuration.add(res.timings.duration);
    check(res, { 'list contacts: 200': (r) => r.status === 200 });
  } else if (roll < 0.7 && tenant.contactIds.length > 0) {
    const id = tenant.contactIds[Math.floor(Math.random() * tenant.contactIds.length)];
    const res = http.get(`${BASE_URL}/api/contacts/${id}`, { ...headers, tags: { name: 'get_contact' } });
    getContactDuration.add(res.timings.duration);
    check(res, { 'get contact: 200': (r) => r.status === 200 });
  } else if (roll < 0.85) {
    const res = http.post(
      `${BASE_URL}/api/contacts`,
      JSON.stringify({
        name: `Contact ${__VU}-${__ITER}`,
        email: `c-${__VU}-${__ITER}-${Date.now()}@loadtest.local`,
        phone: '555-0101',
      }),
      { ...headers, tags: { name: 'create_contact' } }
    );
    createContactDuration.add(res.timings.duration);
    const created = check(res, { 'create contact: 201': (r) => r.status === 201 });
    if (created) tenant.contactIds.push(res.json('id'));
  } else if (tenant.contactIds.length > 0) {
    const contactId = tenant.contactIds[Math.floor(Math.random() * tenant.contactIds.length)];
    const res = http.post(
      `${BASE_URL}/api/tickets`,
      JSON.stringify({ contactId, title: `Issue ${__VU}-${__ITER}`, status: 'OPEN', priority: 'MEDIUM' }),
      { ...headers, tags: { name: 'create_ticket' } }
    );
    createTicketDuration.add(res.timings.duration);
    check(res, { 'create ticket: 201': (r) => r.status === 201 });
  }

  sleep(0.2 + Math.random());
}
