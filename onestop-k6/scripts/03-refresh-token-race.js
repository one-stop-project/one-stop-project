import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { url, PATHS } from '../lib/config.js';
import { login, required, headers } from '../lib/common.js';
const success = new Counter('refresh_success');
const rejected = new Counter('refresh_rejected');
const unexpected = new Rate('refresh_unexpected');
export const options = { scenarios: { race: { executor: 'shared-iterations', vus: Number(__ENV.VUS || 10), iterations: Number(__ENV.ITERATIONS || 10), maxDuration: '20s' } }, thresholds: { refresh_unexpected: ['rate<0.01'] } };
export function setup() {
  const deviceId = __ENV.DEVICE_ID || '11111111-1111-4111-8111-111111111111';
  const result = login(required('EMAIL'), required('PASSWORD'), deviceId);
  const refreshUrl = url(PATHS.refresh);
  const cookies = result.jar.cookiesForURL(refreshUrl);
  const cookieHeader = Object.entries(cookies).flatMap(([n, vs]) => vs.map(v => `${n}=${v}`)).join('; ');
  return { cookieHeader };
}
export default function (data) {
  const r = http.post(url(PATHS.refresh), __ENV.REFRESH_BODY || '{}', { headers: headers(null, { Cookie: data.cookieHeader, 'User-Agent': 'k6-rtr-race/1.0' }), tags: { name: 'refresh race' } });
  if (r.status >= 200 && r.status < 300) success.add(1); else if ([400,401,409,429].includes(r.status)) rejected.add(1); else unexpected.add(true);
  check(r, { 'expected refresh result': x => (x.status >= 200 && x.status < 300) || [400,401,409,429].includes(x.status) });
}
