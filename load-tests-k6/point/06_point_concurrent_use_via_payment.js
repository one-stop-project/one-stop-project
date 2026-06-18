import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, USER_EMAIL, DEFAULT_PASSWORD, login, authHeaders, dataOf, record, thresholdBase } from '../lib/common.js';

const orderCreated = new Counter('order_created_count');
const paymentSuccess = new Counter('payment_success_count');
const pointUseExpectedFail = new Counter('point_use_expected_fail_count');

export const options = {
  scenarios: {
    point_use_via_payment: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 1000),
      iterations: Number(__ENV.ITERATIONS || 1000),
      maxDuration: __ENV.MAX_DURATION || '5m',
    },
  },
  thresholds: Object.assign(thresholdBase(), {
    server_error_rate: ['rate<0.01'],
  }),
};

function itemIds() {
  return (__ENV.ITEM_IDS || __ENV.ITEM_ID || '').split(',').map(s => s.trim()).filter(Boolean).map(Number);
}

export function setup() {
  const ids = itemIds();
  if (ids.length === 0) throw new Error('Set ITEM_ID or ITEM_IDS env. Example: ITEM_ID=1');

  const s = login(USER_EMAIL, DEFAULT_PASSWORD);
  if (!s.ok) throw new Error(`login failed: ${s.res.status} ${s.res.body}`);

  const preload = Number(__ENV.PRELOAD_POINT || 10000);
  if (preload > 0) {
    const charge = http.post(`${BASE_URL}/api/users/me/points/charge`, JSON.stringify({ amount: preload }), {
      headers: authHeaders(s),
    });
    if (![200, 201].includes(charge.status)) {
      console.warn(`preload charge failed. status=${charge.status} body=${charge.body}`);
    }
  }

  return { session: s, itemIds: ids };
}

export default function (data) {
  const usedPoint = Number(__ENV.USED_POINT || 100);
  const itemId = data.itemIds[(__VU + __ITER) % data.itemIds.length];

  const orderReq = {
    orderType: 'DIRECT',
    items: [{ itemId, quantity: 1 }],
    cartItemIds: null,
    receiverName: 'k6포인트',
    receiverPhone: '010-0000-0000',
    receiverAddress: '서울시 테스트구',
    deliveryMessage: 'k6 point use test',
    userCouponId: null,
    usedPoint,
  };

  const orderRes = http.post(`${BASE_URL}/api/orders`, JSON.stringify(orderReq), { headers: authHeaders(data.session) });
  if (orderRes.status === 201 || orderRes.status === 200) orderCreated.add(1);

  const order = dataOf(orderRes);
  if (!order.orderId) {
    record(orderRes, r => [200, 201, 400, 401, 403, 409, 429].includes(r.status));
    check(orderRes, { 'order failure is expected': r => r.status < 500 });
    return;
  }

  const payReq = { orderId: order.orderId, amount: order.finalPrice };
  const payRes = http.post(`${BASE_URL}/api/payments`, JSON.stringify(payReq), { headers: authHeaders(data.session) });

  if (payRes.status === 200) paymentSuccess.add(1);
  else if ([400, 401, 403, 409, 429].includes(payRes.status)) pointUseExpectedFail.add(1);

  record(payRes, r => [200, 400, 401, 403, 409, 429].includes(r.status));

  check(payRes, {
    'payment expected status': r => [200, 400, 401, 403, 409, 429].includes(r.status),
    'no server error': r => r.status < 500,
  });
}

export function teardown(data) {
  const res = http.get(`${BASE_URL}/api/users/me/points`, { headers: authHeaders(data.session) });
  check(res, { 'point balance readable after use test': r => r.status === 200 });
  console.log(`Final point response after point-use test: ${JSON.stringify(dataOf(res))}`);
  console.log('Expected consistency checks: balance >= 0, PointHistory USE count == payment_success_count, PointUsageDetail sum == payment_success_count * USED_POINT');
}
