import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { url, PATHS, replacePath } from '../lib/config.js';
import { headers, required } from '../lib/common.js';
const issued = new Counter('coupon_issued');
const rejected = new Counter('coupon_rejected');
const unexpected = new Rate('coupon_unexpected');
export const options = { scenarios: { issue: { executor: 'per-vu-iterations', vus: Number(__ENV.VUS || 100), iterations: 1, maxDuration: '30s' } }, thresholds: { coupon_unexpected: ['rate<0.01'] } };
export default function () {
  const tokens = JSON.parse(required('ACCESS_TOKENS_JSON'));
  const r = http.post(url(replacePath(PATHS.couponIssue, { couponId: required('COUPON_ID') })), null, { headers: headers(tokens[(__VU-1)%tokens.length]), tags: { name: 'coupon issue' } });
  if (r.status >= 200 && r.status < 300) issued.add(1); else if ([400,409,429].includes(r.status)) rejected.add(1); else unexpected.add(true);
  check(r, { 'expected coupon result': x => (x.status >= 200 && x.status < 300) || [400,409,429].includes(x.status) });
}
