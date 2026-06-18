import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

export function requiredEnv(name) {
  const value = __ENV[name];
  if (!value || String(value).trim().length === 0) {
    throw new Error(`${name} env is required`);
  }
  return value;
}

export const BASE_URL = requiredEnv('BASE_URL').replace(/\/$/, '');
export const DEFAULT_PASSWORD = requiredEnv('DEFAULT_PASSWORD');
export const USER_EMAIL = __ENV.USER_EMAIL || 'loadtest@example.com';
export const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@example.com';
export const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || DEFAULT_PASSWORD;

export const okRate = new Rate('business_ok_rate');
export const serverErrorRate = new Rate('server_error_rate');
export const blockedCounter = new Counter('blocked_count');
export const businessFailCounter = new Counter('business_fail_count');
export const latencyTrend = new Trend('business_latency_ms', true);

export function jsonHeaders(extra = {}) {
  return Object.assign({ 'Content-Type': 'application/json' }, extra);
}

export function parseJson(res) {
  try { return res.json(); } catch (e) { return {}; }
}

export function dataOf(res) {
  const body = parseJson(res);
  return body && body.data ? body.data : {};
}

export function extractSetCookies(res) {
  const raw = res.headers['Set-Cookie'] || res.headers['set-cookie'] || '';
  if (!raw) return {};

  const values = Array.isArray(raw) ? raw : splitSetCookie(String(raw));
  const out = {};
  for (const line of values) {
    const first = String(line).split(';')[0];
    const idx = first.indexOf('=');
    if (idx > 0) out[first.slice(0, idx).trim()] = first.slice(idx + 1).trim();
  }
  return out;
}

// Set-Cookie can contain comma inside Expires. This splitter is good enough for k6/Golang header serialization.
function splitSetCookie(header) {
  return header.split(/,(?=\s*[^;,\s]+=)/g).map(v => v.trim()).filter(Boolean);
}

export function cookieHeader(cookies) {
  return Object.entries(cookies)
    .filter(([_, v]) => v !== undefined && v !== null && String(v).length > 0)
    .map(([k, v]) => `${k}=${v}`)
    .join('; ');
}

export function login(email = USER_EMAIL, password = DEFAULT_PASSWORD, extraHeaders = {}, deviceId = null) {
  const headers = jsonHeaders(extraHeaders);
  if (deviceId) headers['Cookie'] = `device_id=${deviceId}`;

  const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email, password }), { headers });
  const cookies = extractSetCookies(res);
  const data = dataOf(res);
  const accessToken = data.accessToken;

  const passed = check(res, {
    'login status is 200': r => r.status === 200,
    'login has accessToken': _ => !!accessToken,
    'login has refresh cookie': _ => !!cookies.refresh_token,
    'login has device_id cookie': _ => !!cookies.device_id || !!deviceId,
  });

  return {
    ok: passed && res.status === 200,
    res,
    accessToken,
    userId: data.userId,
    cookies: Object.assign({}, deviceId ? { device_id: deviceId } : {}, cookies),
  };
}

export function authHeaders(session, extra = {}) {
  const headers = jsonHeaders(extra);
  if (session && session.accessToken) headers.Authorization = `Bearer ${session.accessToken}`;
  if (session && session.cookies) headers.Cookie = cookieHeader(session.cookies);
  return headers;
}

export function record(res, okPredicate = r => r.status >= 200 && r.status < 300) {
  const ok = okPredicate(res);
  okRate.add(ok);
  serverErrorRate.add(res.status >= 500);
  latencyTrend.add(res.timings.duration);
  if (!ok) businessFailCounter.add(1);
  return ok;
}

export function randomDeviceId(prefix = 'k6-device') {
  return `${prefix}-${Date.now()}-${__VU}-${__ITER}-${Math.random().toString(16).slice(2)}`;
}

export function uuidLike(prefix = 'k6') {
  return `${prefix}-${Date.now()}-${__VU}-${__ITER}-${Math.random().toString(16).slice(2)}`;
}

export function sleepJitter(base = 0.1, spread = 0.2) {
  sleep(base + Math.random() * spread);
}

export function thresholdBase() {
  return {
    http_req_failed: ['rate<0.20'],
    http_req_duration: ['p(95)<3000', 'p(99)<7000'],
    server_error_rate: ['rate<0.01'],
  };
}
