import http from 'k6/http';
import { check, fail } from 'k6';
import { url, PATHS } from './config.js';
export function headers(token, extra = {}) {
  const h = { 'Content-Type': 'application/json', ...extra };
  if (token) h.Authorization = `Bearer ${token}`;
  return h;
}
export function bodyOf(r) { try { return r.json(); } catch (_) { return null; } }
export function unwrap(b) { return b?.data ?? b?.result ?? b; }
export function accessTokenOf(r) {
  const b = unwrap(bodyOf(r));
  return b?.accessToken ?? b?.access_token ?? b?.token ?? null;
}
export function required(name) {
  const v = __ENV[name];
  if (!v) fail(`Missing environment variable: ${name}`);
  return v;
}
export function login(email, password, deviceId) {
  const jar = http.cookieJar();
  jar.set(__ENV.BASE_URL || 'http://localhost:8080', 'device_id', deviceId, { path: '/' });
  const r = http.post(url(PATHS.login), JSON.stringify({ email, password }), {
    headers: headers(null, { 'User-Agent': 'k6-onestop/1.0' }),
    tags: { name: 'POST /api/auth/login' },
  });
  if (!check(r, { 'login 2xx': x => x.status >= 200 && x.status < 300, 'access token exists': x => !!accessTokenOf(x) })) {
    fail(`login failed: ${r.status} ${r.body}`);
  }
  return { response: r, accessToken: accessTokenOf(r), jar };
}
