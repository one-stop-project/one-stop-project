import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, ADMIN_EMAIL, ADMIN_PASSWORD, login, authHeaders, record, thresholdBase } from '../lib/common.js';

const batchRunSuccess = new Counter('point_expire_batch_success_count');

export const options = {
  scenarios: {
    manual_expire_batch: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: Number(__ENV.ITERATIONS || 2),
      maxDuration: __ENV.MAX_DURATION || '10m',
    },
  },
  thresholds: Object.assign(thresholdBase(), {
    point_expire_batch_success_count: ['count>=1'],
    server_error_rate: ['rate<0.01'],
  }),
};

export function setup() {
  const s = login(ADMIN_EMAIL, ADMIN_PASSWORD);
  if (!s.ok) throw new Error(`admin login failed: ${s.res.status} ${s.res.body}`);
  return { session: s };
}

export default function (data) {
  const targetDate = __ENV.TARGET_DATE || new Date().toISOString().slice(0, 10);
  const res = http.post(`${BASE_URL}/api/admin/points/expire/run?targetDate=${encodeURIComponent(targetDate)}`, null, {
    headers: authHeaders(data.session),
    timeout: __ENV.TIMEOUT || '600s',
  });

  if (res.status === 200) batchRunSuccess.add(1);
  record(res, r => [200, 400, 401, 403, 409, 429].includes(r.status));

  check(res, {
    'batch run expected status': r => [200, 400, 401, 403, 409, 429].includes(r.status),
    'no server error': r => r.status < 500,
  });

  console.log(`expire batch response: status=${res.status} body=${res.body}`);
}
