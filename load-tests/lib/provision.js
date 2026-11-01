import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const ADMIN_USER = __ENV.ADMIN_USER || 'platform-admin';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'change-me-dev-only';
const TENANT_PASSWORD = 'LoadTest123!';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function adminBaseUrl() {
  return BASE_URL.replace('://', `://${ADMIN_USER}:${ADMIN_PASSWORD}@`);
}

export function authHeaders(token) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } };
}

/**
 * Onboards one tenant (platform-admin API) and logs in as its admin user, so callers get back a
 * ready-to-use bearer token. `label` only has to be unique within a single setup() run - it goes
 * into the tenant name/admin email alongside a timestamp so re-running a script against a
 * long-lived database never collides with a previous run's tenants.
 */
export function provisionTenant(label, plan) {
  const suffix = `${label}-${Date.now()}-${Math.floor(Math.random() * 100000)}`;
  const adminEmail = `admin-${suffix}@loadtest.local`;

  const onboardRes = http.post(
    `${adminBaseUrl()}/api/tenants`,
    JSON.stringify({ name: `loadtest-${suffix}`, plan, adminEmail, adminPassword: TENANT_PASSWORD }),
    { headers: JSON_HEADERS }
  );
  if (!check(onboardRes, { 'tenant onboarded (201)': (r) => r.status === 201 })) {
    fail(`onboarding tenant ${suffix} failed: ${onboardRes.status} ${onboardRes.body}`);
  }
  const tenantId = onboardRes.json('tenantId');

  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ tenantId, email: adminEmail, password: TENANT_PASSWORD }),
    { headers: JSON_HEADERS }
  );
  if (!check(loginRes, { 'tenant admin login (200)': (r) => r.status === 200 })) {
    fail(`login for tenant ${suffix} failed: ${loginRes.status} ${loginRes.body}`);
  }

  return { tenantId, plan, token: loginRes.json('token') };
}

/** Creates one contact for the tenant and returns its id, or null if seeding failed. */
export function seedContact(token, label) {
  const res = http.post(
    `${BASE_URL}/api/contacts`,
    JSON.stringify({ name: `Seed ${label}`, email: `seed-${label}-${Date.now()}@loadtest.local`, phone: '555-0100' }),
    authHeaders(token)
  );
  return res.status === 201 ? res.json('id') : null;
}
