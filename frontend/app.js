'use strict';

/* ============================== state & storage ============================== */

const DEFAULT_API_BASE = 'http://localhost:8080';

function getApiBase() {
  return localStorage.getItem('crm.apiBase') || DEFAULT_API_BASE;
}
function setApiBase(value) {
  localStorage.setItem('crm.apiBase', value);
}

// sessionStorage (not localStorage) for the logged-in session on purpose: each browser tab can
// then represent a different tenant at once, which is itself a nice live demo of isolation —
// open two tabs, log into two different tenants, see different data in each.
const session = {
  get token() { return sessionStorage.getItem('crm.token'); },
  get tenantId() { return sessionStorage.getItem('crm.tenantId'); },
  get role() { return sessionStorage.getItem('crm.role'); },
  get email() { return sessionStorage.getItem('crm.email'); },
  set(token, claims) {
    sessionStorage.setItem('crm.token', token);
    sessionStorage.setItem('crm.tenantId', claims.tenant_id || '');
    sessionStorage.setItem('crm.role', claims.role || '');
    sessionStorage.setItem('crm.email', claims.sub || '');
  },
  clear() {
    sessionStorage.removeItem('crm.token');
    sessionStorage.removeItem('crm.tenantId');
    sessionStorage.removeItem('crm.role');
    sessionStorage.removeItem('crm.email');
  },
  get isLoggedIn() { return !!this.token; },
};

function decodeJwt(token) {
  const payload = token.split('.')[1];
  const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
  const json = decodeURIComponent(
    atob(base64)
      .split('')
      .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
      .join('')
  );
  return JSON.parse(json);
}

function rememberTenant(tenantId, name, email) {
  const list = JSON.parse(localStorage.getItem('crm.recentTenants') || '[]').filter((t) => t.tenantId !== tenantId);
  list.unshift({ tenantId, name, email });
  localStorage.setItem('crm.recentTenants', JSON.stringify(list.slice(0, 6)));
}
function recentTenants() {
  return JSON.parse(localStorage.getItem('crm.recentTenants') || '[]');
}

/* ============================== API client ============================== */

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function apiFetch(path, { method = 'GET', body, headers = {}, auth = true } = {}) {
  const finalHeaders = { ...headers };
  if (body !== undefined) finalHeaders['Content-Type'] = 'application/json';
  if (auth && session.token) finalHeaders['Authorization'] = 'Bearer ' + session.token;
  // Idempotency-Key only matters to the backend on POST (IdempotencyFilter - see ADR 0008); a
  // fresh one per call is exactly the right behavior for a form submit that isn't being retried.
  if (method === 'POST') finalHeaders['Idempotency-Key'] = crypto.randomUUID();

  let response;
  try {
    response = await fetch(getApiBase() + path, {
      method,
      headers: finalHeaders,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch (networkError) {
    throw new ApiError(
      'Could not reach the API at ' + getApiBase() + ' — is it running, and is CORS configured for this origin?',
      0
    );
  }

  if (response.status === 429) {
    const retryAfter = parseInt(response.headers.get('Retry-After') || '5', 10);
    showRateLimitBanner(retryAfter);
    throw new ApiError('Rate limited — retry in ' + retryAfter + 's.', 429);
  }

  if (response.status === 204) return null;

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const message = (data && (data.detail || data.title)) || response.statusText || 'Request failed';
    throw new ApiError(message, response.status);
  }
  return data;
}

function adminFetch(path, { method = 'GET', body, username, password } = {}) {
  const basic = btoa(username + ':' + password);
  return apiFetch(path, { method, body, headers: { Authorization: 'Basic ' + basic }, auth: false });
}

/* ============================== toasts & rate-limit banner ============================== */

function toast(message, kind = 'info') {
  const el = document.createElement('div');
  el.className = 'toast' + (kind === 'error' ? ' toast-error' : kind === 'success' ? ' toast-success' : '');
  el.textContent = message;
  document.getElementById('toasts').appendChild(el);
  setTimeout(() => el.remove(), 5000);
}

let rateLimitTimer = null;
function showRateLimitBanner(seconds) {
  const banner = document.getElementById('rate-limit-banner');
  const countdown = document.getElementById('rate-limit-countdown');
  banner.classList.remove('hidden');
  let remaining = seconds;
  countdown.textContent = remaining;
  clearInterval(rateLimitTimer);
  rateLimitTimer = setInterval(() => {
    remaining -= 1;
    countdown.textContent = Math.max(remaining, 0);
    if (remaining <= 0) {
      clearInterval(rateLimitTimer);
      banner.classList.add('hidden');
    }
  }, 1000);
}

/* ============================== small render helpers ============================== */

function escapeHtml(value) {
  const div = document.createElement('div');
  div.textContent = value ?? '';
  return div.innerHTML;
}
function shortId(id) {
  return id ? id.slice(0, 8) + '…' : '';
}
function fmtDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleString();
}

function renderPagination(containerId, page, onGoTo) {
  const el = document.getElementById(containerId);
  if (!page || page.totalPages <= 1) {
    el.innerHTML = '';
    return;
  }
  el.innerHTML = '';
  const prev = document.createElement('button');
  prev.textContent = 'Prev';
  prev.className = 'btn-secondary';
  prev.disabled = page.number <= 0;
  prev.onclick = () => onGoTo(page.number - 1);

  const next = document.createElement('button');
  next.textContent = 'Next';
  next.className = 'btn-secondary';
  next.disabled = page.number >= page.totalPages - 1;
  next.onclick = () => onGoTo(page.number + 1);

  const label = document.createElement('span');
  label.textContent = `Page ${page.number + 1} of ${page.totalPages} (${page.totalElements} total)`;

  el.append(prev, label, next);
}

function applyRoleVisibility() {
  const isAdmin = session.role === 'ADMIN';
  const canWrite = session.role === 'ADMIN' || session.role === 'AGENT';
  document.querySelectorAll('.admin-only').forEach((el) => el.classList.toggle('hidden', !isAdmin));
  document.querySelectorAll('.write-only').forEach((el) => el.classList.toggle('hidden', !canWrite));
}

/* ============================== view / tab switching ============================== */

function showView(name) {
  document.getElementById('view-login').classList.toggle('hidden', name !== 'login');
  document.getElementById('view-dashboard').classList.toggle('hidden', name !== 'dashboard');
}

function switchTab(tab) {
  document.querySelectorAll('.tab-btn').forEach((b) => b.classList.toggle('active', b.dataset.tab === tab));
  document.querySelectorAll('.tab-panel').forEach((p) => p.classList.toggle('active', p.id === 'tab-' + tab));
  if (tab === 'contacts') loadContacts(0);
  if (tab === 'tickets') loadTickets(0);
  if (tab === 'webhooks') { loadWebhooks(); loadDeadLetters(0); }
  if (tab === 'audit') loadAuditLog(0);
}

/* ============================== onboarding & login ============================== */

async function handleOnboard(event) {
  event.preventDefault();
  const form = event.target;
  const data = Object.fromEntries(new FormData(form).entries());
  const resultEl = document.getElementById('onboard-result');
  try {
    const response = await adminFetch('/api/tenants', {
      method: 'POST',
      username: data.adminUser,
      password: data.adminPass,
      body: { name: data.tenantName, plan: data.plan, adminEmail: data.tenantAdminEmail, adminPassword: data.tenantAdminPassword },
    });
    resultEl.className = 'result';
    resultEl.innerHTML =
      `Tenant created: <span class="mono">${escapeHtml(response.tenantId)}</span> ` +
      `<button type="button" id="btn-use-tenant" class="btn-secondary">Use for login →</button>`;
    resultEl.classList.remove('hidden');
    document.getElementById('btn-use-tenant').onclick = () => {
      const loginForm = document.getElementById('form-login');
      loginForm.tenantId.value = response.tenantId;
      loginForm.email.value = data.tenantAdminEmail;
      loginForm.password.focus();
    };
  } catch (err) {
    resultEl.className = 'result result-error';
    resultEl.textContent = err.message;
    resultEl.classList.remove('hidden');
  }
}

async function handleLogin(event) {
  event.preventDefault();
  const form = event.target;
  const data = Object.fromEntries(new FormData(form).entries());
  const errorEl = document.getElementById('login-error');
  errorEl.classList.add('hidden');
  try {
    const response = await apiFetch('/api/auth/login', { method: 'POST', auth: false, body: data });
    const claims = decodeJwt(response.token);
    session.set(response.token, claims);
    rememberTenant(data.tenantId, data.tenantId, data.email);
    await enterDashboard();
  } catch (err) {
    errorEl.textContent = err.message;
    errorEl.classList.remove('hidden');
  }
}

function logout() {
  session.clear();
  showView('login');
  renderRecentTenants();
}

async function enterDashboard() {
  applyRoleVisibility();
  document.getElementById('dash-role').textContent = session.role;
  document.getElementById('dash-email').textContent = session.email;
  document.getElementById('dash-tenant-id').textContent = session.tenantId;
  showView('dashboard');
  switchTab('contacts');
  try {
    const me = await apiFetch('/api/tenants/me');
    document.getElementById('dash-tenant-name').textContent = me.name;
    document.getElementById('dash-plan').textContent = `${me.plan} · ${me.rateLimitPerMinute} req/min`;
    rememberTenant(me.tenantId, me.name, session.email);
    renderRecentTenants();
  } catch (err) {
    toast('Could not load tenant info: ' + err.message, 'error');
  }
}

function renderRecentTenants() {
  const list = recentTenants();
  const wrap = document.getElementById('recent-tenants');
  const ul = document.getElementById('recent-tenants-list');
  if (list.length === 0) {
    wrap.classList.add('hidden');
    return;
  }
  wrap.classList.remove('hidden');
  ul.innerHTML = '';
  list.forEach((t) => {
    const li = document.createElement('li');
    li.innerHTML = `<span>${escapeHtml(t.name)}</span><span class="mono muted">${shortId(t.tenantId)}</span>`;
    li.onclick = () => {
      const form = document.getElementById('form-login');
      form.tenantId.value = t.tenantId;
      form.email.value = t.email;
      form.password.focus();
    };
    ul.appendChild(li);
  });
}

/* ============================== contacts ============================== */

let contactsCache = [];

async function loadContacts(pageNumber) {
  try {
    const data = await apiFetch(`/api/contacts?page=${pageNumber}&size=10`);
    contactsCache = data.content;
    const tbody = document.getElementById('contacts-tbody');
    tbody.innerHTML = '';
    if (data.content.length === 0) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="5">No contacts yet.</td></tr>';
    }
    data.content.forEach((c) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>${escapeHtml(c.name)}</td>
        <td>${escapeHtml(c.email)}</td>
        <td>${escapeHtml(c.phone || '—')}</td>
        <td>${fmtDate(c.createdAt)}</td>
        <td class="actions-cell write-only">
          <button class="btn-icon" data-action="edit">Edit</button>
          <button class="btn-icon btn-danger" data-action="delete">Delete</button>
        </td>`;
      tr.querySelector('[data-action="edit"]')?.addEventListener('click', () => openContactDialog(c));
      tr.querySelector('[data-action="delete"]')?.addEventListener('click', () => deleteContact(c.id, pageNumber));
      tbody.appendChild(tr);
    });
    applyRoleVisibility();
    renderPagination('contacts-pagination', data.page, loadContacts);
  } catch (err) {
    toast('Failed to load contacts: ' + err.message, 'error');
  }
}

function openContactDialog(contact) {
  const dlg = document.getElementById('dlg-contact');
  const form = document.getElementById('form-contact');
  form.reset();
  form.id.value = contact ? contact.id : '';
  if (contact) {
    form.name.value = contact.name;
    form.email.value = contact.email;
    form.phone.value = contact.phone || '';
  }
  document.getElementById('dlg-contact-title').textContent = contact ? 'Edit contact' : 'New contact';
  dlg.showModal();
}

async function handleContactSubmit(event) {
  event.preventDefault();
  const form = event.target;
  const data = Object.fromEntries(new FormData(form).entries());
  try {
    if (data.id) {
      await apiFetch(`/api/contacts/${data.id}`, { method: 'PUT', body: { name: data.name, email: data.email, phone: data.phone } });
      toast('Contact updated', 'success');
    } else {
      await apiFetch('/api/contacts', { method: 'POST', body: { name: data.name, email: data.email, phone: data.phone } });
      toast('Contact created', 'success');
    }
    document.getElementById('dlg-contact').close();
    loadContacts(0);
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function deleteContact(id, currentPage) {
  if (!confirm('Delete this contact?')) return;
  try {
    await apiFetch(`/api/contacts/${id}`, { method: 'DELETE' });
    toast('Contact deleted', 'success');
    loadContacts(currentPage);
  } catch (err) {
    toast(err.message, 'error');
  }
}

/* ============================== tickets ============================== */

async function loadTickets(pageNumber) {
  try {
    const data = await apiFetch(`/api/tickets?page=${pageNumber}&size=10`);
    const tbody = document.getElementById('tickets-tbody');
    tbody.innerHTML = '';
    if (data.content.length === 0) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="6">No tickets yet.</td></tr>';
    }
    data.content.forEach((t) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>${escapeHtml(t.title)}</td>
        <td><span class="badge badge-muted">${escapeHtml(t.status)}</span></td>
        <td>${escapeHtml(t.priority)}</td>
        <td class="mono muted">${shortId(t.contactId)}</td>
        <td>${fmtDate(t.createdAt)}</td>
        <td class="actions-cell">
          <button class="btn-icon" data-action="comments">Comments</button>
          <button class="btn-icon write-only" data-action="edit">Edit</button>
          <button class="btn-icon btn-danger write-only" data-action="delete">Delete</button>
        </td>`;
      tr.querySelector('[data-action="comments"]').addEventListener('click', () => openCommentsDialog(t.id));
      tr.querySelector('[data-action="edit"]')?.addEventListener('click', () => openTicketDialog(t));
      tr.querySelector('[data-action="delete"]')?.addEventListener('click', () => deleteTicket(t.id, pageNumber));
      tbody.appendChild(tr);
    });
    applyRoleVisibility();
    renderPagination('tickets-pagination', data.page, loadTickets);
  } catch (err) {
    toast('Failed to load tickets: ' + err.message, 'error');
  }
}

async function openTicketDialog(ticket) {
  const dlg = document.getElementById('dlg-ticket');
  const form = document.getElementById('form-ticket');
  form.reset();
  form.id.value = ticket ? ticket.id : '';

  // Contacts are paginated server-side; a single page of up to 100 is plenty for a dropdown in
  // this demo rather than building a separate search-as-you-type contact picker.
  try {
    const contacts = await apiFetch('/api/contacts?size=100');
    if (contacts.content.length === 0) {
      toast('Create a contact first — tickets need one to attach to.', 'error');
      return;
    }
    form.contactId.innerHTML = contacts.content
      .map((c) => `<option value="${c.id}">${escapeHtml(c.name)} (${escapeHtml(c.email)})</option>`)
      .join('');
  } catch (err) {
    toast('Could not load contacts for the dropdown: ' + err.message, 'error');
    return;
  }

  document.getElementById('dlg-ticket-title').textContent = ticket ? 'Edit ticket' : 'New ticket';
  if (ticket) {
    form.contactId.value = ticket.contactId;
    form.title.value = ticket.title;
    form.status.value = ticket.status;
    form.priority.value = ticket.priority;
    form.assigneeId.value = ticket.assigneeId || '';
  }
  dlg.showModal();
}

async function handleTicketSubmit(event) {
  event.preventDefault();
  const form = event.target;
  const data = Object.fromEntries(new FormData(form).entries());
  const body = {
    contactId: data.contactId,
    title: data.title,
    status: data.status,
    priority: data.priority,
    assigneeId: data.assigneeId || null,
  };
  try {
    if (data.id) {
      await apiFetch(`/api/tickets/${data.id}`, { method: 'PUT', body });
      toast('Ticket updated', 'success');
    } else {
      await apiFetch('/api/tickets', { method: 'POST', body });
      toast('Ticket created', 'success');
    }
    document.getElementById('dlg-ticket').close();
    loadTickets(0);
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function deleteTicket(id, currentPage) {
  if (!confirm('Delete this ticket?')) return;
  try {
    await apiFetch(`/api/tickets/${id}`, { method: 'DELETE' });
    toast('Ticket deleted', 'success');
    loadTickets(currentPage);
  } catch (err) {
    toast(err.message, 'error');
  }
}

let activeTicketId = null;

async function openCommentsDialog(ticketId) {
  activeTicketId = ticketId;
  await loadComments();
  applyRoleVisibility();
  document.getElementById('dlg-comments').showModal();
}

async function loadComments() {
  const list = document.getElementById('comments-list');
  try {
    const data = await apiFetch(`/api/tickets/${activeTicketId}/comments?size=50`);
    list.innerHTML = data.content.length
      ? data.content
          .map(
            (c) => `<div class="comment-item">${escapeHtml(c.body)}
              <div class="comment-meta mono">${fmtDate(c.createdAt)} · actor ${shortId(c.authorId)}</div></div>`
          )
          .join('')
      : '<p class="muted">No comments yet.</p>';
  } catch (err) {
    list.innerHTML = `<p class="result-error">${escapeHtml(err.message)}</p>`;
  }
}

async function handleCommentSubmit(event) {
  event.preventDefault();
  const form = event.target;
  const body = Object.fromEntries(new FormData(form).entries());
  try {
    await apiFetch(`/api/tickets/${activeTicketId}/comments`, { method: 'POST', body });
    form.reset();
    await loadComments();
  } catch (err) {
    toast(err.message, 'error');
  }
}

/* ============================== webhooks ============================== */

async function loadWebhooks() {
  try {
    const subscriptions = await apiFetch('/api/webhooks');
    const tbody = document.getElementById('webhooks-tbody');
    tbody.innerHTML = subscriptions.length
      ? ''
      : '<tr class="empty-row"><td colspan="4">No webhook subscriptions yet.</td></tr>';
    subscriptions.forEach((s) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td class="mono">${escapeHtml(s.url)}</td>
        <td>${s.eventTypes.map((e) => `<span class="badge badge-muted">${escapeHtml(e)}</span>`).join(' ')}</td>
        <td>${fmtDate(s.createdAt)}</td>
        <td><button class="btn-icon btn-danger" data-action="delete">Delete</button></td>`;
      tr.querySelector('[data-action="delete"]').addEventListener('click', () => deleteWebhook(s.id));
      tbody.appendChild(tr);
    });
  } catch (err) {
    toast('Failed to load webhooks: ' + err.message, 'error');
  }
}

async function deleteWebhook(id) {
  if (!confirm('Delete this webhook subscription?')) return;
  try {
    await apiFetch(`/api/webhooks/${id}`, { method: 'DELETE' });
    toast('Subscription deleted', 'success');
    loadWebhooks();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function handleWebhookSubmit(event) {
  event.preventDefault();
  const form = event.target;
  const formData = new FormData(form);
  const eventTypes = formData.getAll('eventTypes');
  if (eventTypes.length === 0) {
    toast('Pick at least one event type', 'error');
    return;
  }
  try {
    const response = await apiFetch('/api/webhooks', { method: 'POST', body: { url: formData.get('url'), eventTypes } });
    document.getElementById('dlg-webhook').close();
    form.reset();
    loadWebhooks();
    document.getElementById('secret-value').textContent = response.secret;
    document.getElementById('dlg-secret').showModal();
  } catch (err) {
    toast(err.message, 'error');
  }
}

async function loadDeadLetters(pageNumber) {
  try {
    const data = await apiFetch(`/api/webhooks/dead-letters?page=${pageNumber}&size=10`);
    const tbody = document.getElementById('dead-letters-tbody');
    tbody.innerHTML = data.content.length
      ? ''
      : '<tr class="empty-row"><td colspan="5">No failed deliveries — nice.</td></tr>';
    data.content.forEach((d) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>${escapeHtml(d.eventType)}</td>
        <td class="mono muted">${shortId(d.subscriptionId)}</td>
        <td>${d.attempts}</td>
        <td>${escapeHtml(d.lastError || '—')}</td>
        <td>${fmtDate(d.createdAt)}</td>`;
      tbody.appendChild(tr);
    });
    renderPagination('dead-letters-pagination', data.page, loadDeadLetters);
  } catch (err) {
    toast('Failed to load dead letters: ' + err.message, 'error');
  }
}

/* ============================== audit log ============================== */

async function loadAuditLog(pageNumber) {
  try {
    const data = await apiFetch(`/api/audit-log?page=${pageNumber}&size=15`);
    const tbody = document.getElementById('audit-tbody');
    tbody.innerHTML = data.content.length ? '' : '<tr class="empty-row"><td colspan="6">No audit entries yet.</td></tr>';
    data.content.forEach((entry) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>${fmtDate(entry.createdAt)}</td>
        <td><span class="badge badge-muted">${escapeHtml(entry.action)}</span></td>
        <td>${escapeHtml(entry.entity)}</td>
        <td class="mono muted">${shortId(entry.entityId)}</td>
        <td class="mono muted">${shortId(entry.actorId)}</td>
        <td><button class="btn-icon" data-action="view">View diff</button></td>`;
      tr.querySelector('[data-action="view"]').addEventListener('click', () => showAuditDiff(entry));
      tbody.appendChild(tr);
    });
    renderPagination('audit-pagination', data.page, loadAuditLog);
  } catch (err) {
    toast('Failed to load audit log: ' + err.message, 'error');
  }
}

function showAuditDiff(entry) {
  const pretty = (json) => (json ? JSON.stringify(JSON.parse(json), null, 2) : 'null');
  document.getElementById('dlg-json-title').textContent = `${entry.action} ${entry.entity}`;
  document.getElementById('dlg-json-content').textContent =
    `before:\n${pretty(entry.beforeJson)}\n\nafter:\n${pretty(entry.afterJson)}`;
  document.getElementById('dlg-json').showModal();
}

/* ============================== wiring ============================== */

function closeOnButtons() {
  document.querySelectorAll('[data-close]').forEach((btn) => {
    btn.addEventListener('click', () => btn.closest('dialog').close());
  });
}

document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('api-base-input').value = getApiBase();
  document.getElementById('api-base-input').addEventListener('change', (e) => setApiBase(e.target.value.trim() || DEFAULT_API_BASE));

  document.getElementById('form-onboard').addEventListener('submit', handleOnboard);
  document.getElementById('form-login').addEventListener('submit', handleLogin);
  document.getElementById('btn-logout').addEventListener('click', logout);

  document.querySelectorAll('.tab-btn').forEach((btn) => btn.addEventListener('click', () => switchTab(btn.dataset.tab)));

  document.getElementById('btn-new-contact').addEventListener('click', () => openContactDialog(null));
  document.getElementById('form-contact').addEventListener('submit', handleContactSubmit);

  document.getElementById('btn-new-ticket').addEventListener('click', () => openTicketDialog(null));
  document.getElementById('form-ticket').addEventListener('submit', handleTicketSubmit);
  document.getElementById('form-comment').addEventListener('submit', handleCommentSubmit);

  document.getElementById('btn-new-webhook').addEventListener('click', () => document.getElementById('dlg-webhook').showModal());
  document.getElementById('form-webhook').addEventListener('submit', handleWebhookSubmit);
  document.getElementById('btn-copy-secret').addEventListener('click', () => {
    navigator.clipboard.writeText(document.getElementById('secret-value').textContent);
    toast('Secret copied', 'success');
  });

  closeOnButtons();
  renderRecentTenants();

  if (session.isLoggedIn) {
    enterDashboard();
  } else {
    showView('login');
  }
});
