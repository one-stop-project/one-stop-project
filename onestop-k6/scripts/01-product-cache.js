import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { url, PATHS } from '../lib/config.js';
const latency = new Trend('product_list_latency', true);
const failures = new Rate('product_list_failures');
export const options = {
  scenarios: { load: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 30), timeUnit: '1s', duration: __ENV.DURATION || '1m', preAllocatedVUs: 30, maxVUs: 100 } },
  thresholds: { product_list_failures: ['rate<0.01'], product_list_latency: ['p(95)<500'] },
};
export default function () {
  const q = __ENV.PRODUCT_QUERY || '?page=0&size=20&sort=createdAt,desc';
  const r = http.get(url(`${PATHS.products}${q}`), { tags: { name: 'GET /api/products' } });
  latency.add(r.timings.duration); failures.add(r.status !== 200);
  check(r, { 'status 200': x => x.status === 200 });
}
