import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, USER_EMAIL, DEFAULT_PASSWORD, login, authHeaders, dataOf, record, thresholdBase } from '../lib/common.js';

const chargeSuccess = new Counter('point_charge_success_count');
const chargeFail = new Counter('point_charge_fail_count');

export const options = {
  scenarios: {
    concurrent_charge_same_user: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 100),
      iterations: Number(__ENV.ITERATIONS || 100),
      maxDuration: __ENV.MAX_DURATION || '60s',
    },
  },
  thresholds: Object.assign(thresholdBase(), {
    server_error_rate: ['rate<0.01'],
  }),
};

export function setup() {
  const s = login(USER_EMAIL, DEFAULT_PASSWORD);
  if (!s.ok) throw new Error(`login failed: ${s.res.status} ${s.res.body}`);
  return { session: s };
}

export default function (data) {
  const amount = Number(__ENV.CHARGE_AMOUNT || 1000);
  const res = http.post(`${BASE_URL}/api/users/me/points/charge`, JSON.stringify({ amount }), {
    headers: authHeaders(data.session),
  });

  if (res.status === 200) chargeSuccess.add(1); else chargeFail.add(1);
  record(res, r => [200, 400, 401, 403, 409, 429].includes(r.status));

  check(res, {
    'charge expected status': r => [200, 400, 401, 403, 409, 429].includes(r.status),
    'no server error': r => r.status < 500,
  });
}

export function teardown(data) {
  const res = http.get(`${BASE_URL}/api/users/me/points`, { headers: authHeaders(data.session) });
  check(res, { 'point balance readable after charge': r => r.status === 200 });
  const p = dataOf(res);
  console.log(`Final point response after charge test: ${JSON.stringify(p)}`);
}
