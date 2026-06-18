import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { url, PATHS } from '../lib/config.js';
import { headers, required } from '../lib/common.js';
const orderLatency = new Trend('order_create_latency', true);
const paymentLatency = new Trend('payment_approve_latency', true);
const failures = new Rate('order_payment_failures');
export const options = { scenarios: { flow: { executor: 'constant-vus', vus: Number(__ENV.VUS || 10), duration: __ENV.DURATION || '30s' } }, thresholds: { order_payment_failures: ['rate<0.02'], order_create_latency: ['p(95)<1000'], payment_approve_latency: ['p(95)<1200'] } };
export default function () {
  const users = JSON.parse(required('ORDER_USERS_JSON'));
  const user = users[(__VU-1)%users.length];
  const orderPayload = JSON.parse(required('ORDER_BODY_JSON'));
  const o = http.post(url(PATHS.orderCreate), JSON.stringify(orderPayload), { headers: headers(user.accessToken), tags: { name: 'order create' } });
  orderLatency.add(o.timings.duration);
  let b; try { b=o.json(); } catch (_) { b=null; }
  const d=b?.data??b?.result??b; const orderId=d?.orderId??d?.id;
  if (!check(o, { 'order 2xx': x=>x.status>=200&&x.status<300, 'orderId exists': ()=>!!orderId })) { failures.add(true); return; }
  const paymentPayload = { ...JSON.parse(required('PAYMENT_BODY_TEMPLATE_JSON')), orderId, paymentKey: `k6-${__VU}-${__ITER}-${Date.now()}` };
  const p = http.post(url(PATHS.paymentApprove), JSON.stringify(paymentPayload), { headers: headers(user.accessToken), tags: { name: 'payment approve' } });
  paymentLatency.add(p.timings.duration); const ok=check(p, { 'payment 2xx': x=>x.status>=200&&x.status<300 }); failures.add(!ok); sleep(0.2);
}
