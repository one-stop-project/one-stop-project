import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { url, PATHS } from '../lib/config.js';
import { headers, required } from '../lib/common.js';
const approved = new Counter('point_payment_approved');
const rejected = new Counter('point_payment_rejected');
const unexpected = new Rate('point_payment_unexpected');
export const options = { scenarios: { contention: { executor: 'per-vu-iterations', vus: Number(__ENV.VUS || 5), iterations: 1, maxDuration: '30s' } }, thresholds: { point_payment_unexpected: ['rate<0.01'] } };
export default function () {
  const ids=JSON.parse(required('POINT_ORDER_IDS_JSON')); const orderId=ids[(__VU-1)%ids.length];
  const payload={ ...JSON.parse(required('PAYMENT_BODY_TEMPLATE_JSON')), orderId, paymentKey:`point-race-${__VU}-${Date.now()}` };
  const r=http.post(url(PATHS.paymentApprove), JSON.stringify(payload), { headers:headers(required('ACCESS_TOKEN')), tags:{name:'point contention payment'} });
  if (r.status>=200&&r.status<300) approved.add(1); else if ([400,409,422,429].includes(r.status)) rejected.add(1); else unexpected.add(true);
  check(r, {'controlled result':x=>(x.status>=200&&x.status<300)||[400,409,422,429].includes(x.status)});
}
