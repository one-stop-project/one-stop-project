import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, USER_EMAIL, DEFAULT_PASSWORD, login, authHeaders, dataOf, record, thresholdBase } from '../lib/common.js';

const cancelSuccess = new Counter('cancel_success_count');
const cancelExpectedFail = new Counter('cancel_expected_fail_count');

export const options = {
  scenarios: {
    same_order_cancel_race: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 50),
      iterations: Number(__ENV.ITERATIONS || 50),
      maxDuration: __ENV.MAX_DURATION || '60s',
    },
  },
  thresholds: Object.assign(thresholdBase(), {
    cancel_success_count: ['count==1'],
    server_error_rate: ['rate<0.01'],
  }),
};

function itemId() {
  const id = Number(__ENV.ITEM_ID || ((__ENV.ITEM_IDS || '').split(',')[0] || 0));
  if (!id) throw new Error('Set ITEM_ID env.');
  return id;
}

export function setup() {
  const s = login(USER_EMAIL, DEFAULT_PASSWORD);
  if (!s.ok) throw new Error(`login failed: ${s.res.status} ${s.res.body}`);

  const preload = Number(__ENV.PRELOAD_POINT || 10000);
  const usedPoint = Number(__ENV.USED_POINT || 3000);
  const charge = http.post(`${BASE_URL}/api/users/me/points/charge`, JSON.stringify({ amount: preload }), { headers: authHeaders(s) });
  if (![200, 201].includes(charge.status)) console.warn(`preload charge failed: ${charge.status} ${charge.body}`);

  const orderReq = {
    orderType: 'DIRECT',
    items: [{ itemId: itemId(), quantity: 1 }],
    cartItemIds: null,
    receiverName: 'k6복구',
    receiverPhone: '010-0000-0000',
    receiverAddress: '서울시 테스트구',
    deliveryMessage: 'k6 refund idempotency test',
    userCouponId: null,
    usedPoint,
  };
  const orderRes = http.post(`${BASE_URL}/api/orders`, JSON.stringify(orderReq), { headers: authHeaders(s) });
  if (![200, 201].includes(orderRes.status)) throw new Error(`create order failed: ${orderRes.status} ${orderRes.body}`);
  const order = dataOf(orderRes);

  const payRes = http.post(`${BASE_URL}/api/payments`, JSON.stringify({ orderId: order.orderId, amount: order.finalPrice }), { headers: authHeaders(s) });
  if (payRes.status !== 200) throw new Error(`approve payment failed: ${payRes.status} ${payRes.body}`);

  const before = http.get(`${BASE_URL}/api/users/me/points`, { headers: authHeaders(s) });
  console.log(`Before cancel point response: ${JSON.stringify(dataOf(before))}`);

  return { session: s, orderId: order.orderId };
}

export default function (data) {
  const res = http.post(`${BASE_URL}/api/orders/${data.orderId}/cancel`, JSON.stringify({ reason: 'k6 duplicate cancel race' }), {
    headers: authHeaders(data.session),
  });

  if (res.status === 200) cancelSuccess.add(1);
  else if ([400, 401, 403, 404, 409, 429].includes(res.status)) cancelExpectedFail.add(1);

  record(res, r => [200, 400, 401, 403, 404, 409, 429].includes(r.status));

  check(res, {
    'cancel expected status': r => [200, 400, 401, 403, 404, 409, 429].includes(r.status),
    'no server error': r => r.status < 500,
  });
}

export function teardown(data) {
  const res = http.get(`${BASE_URL}/api/users/me/points`, { headers: authHeaders(data.session) });
  check(res, { 'point balance readable after refund race': r => r.status === 200 });
  console.log(`After cancel point response: ${JSON.stringify(dataOf(res))}`);
  console.log('Expected consistency checks: cancel_success_count == 1, final balance restored once, REFUND PointHistory count == 1 or source-detail count, no duplicate refund.');
}
