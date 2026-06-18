import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { url, PATHS } from '../lib/config.js';
import { headers, required } from '../lib/common.js';
const allowed = new Counter('login_allowed');
const limited = new Counter('login_limited');
const unexpected = new Rate('login_unexpected');
export const options = { scenarios: { burst: { executor: 'per-vu-iterations', vus: Number(__ENV.VUS || 1), iterations: Number(__ENV.ITERATIONS || 8), maxDuration: '30s' } }, thresholds: { login_unexpected: ['rate<0.01'] } };
export default function () {
  const deviceId = __ENV.DEVICE_ID || `00000000-0000-4000-8000-${String(__VU).padStart(12,'0')}`;
  const r = http.post(url(PATHS.login), JSON.stringify({ email: required('EMAIL'), password: required('PASSWORD') }), { headers: headers(null, { Cookie: `device_id=${deviceId}` }), tags: { name: 'login rate-limit' } });
  if (r.status >= 200 && r.status < 300) allowed.add(1); else if (r.status === 429) limited.add(1); else unexpected.add(true);
  check(r, { '2xx or 429': x => (x.status >= 200 && x.status < 300) || x.status === 429 });
}
