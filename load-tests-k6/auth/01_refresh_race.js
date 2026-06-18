import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, USER_EMAIL, DEFAULT_PASSWORD, login, cookieHeader, extractSetCookies, dataOf, record, thresholdBase } from '../lib/common.js';

const successCount = new Counter('refresh_success_count');
const expectedFailCount = new Counter('refresh_expected_fail_count');
const unexpectedSuccessCount = new Counter('refresh_unexpected_success_count');

export const options = {
  scenarios: {
    same_refresh_token_race: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 100),
      iterations: Number(__ENV.ITERATIONS || 100),
      maxDuration: __ENV.MAX_DURATION || '30s',
    },
  },
  thresholds: Object.assign(thresholdBase(), {
    refresh_success_count: ['count==1'],
    server_error_rate: ['rate<0.01'],
  }),
};

export function setup() {
  const s = login(USER_EMAIL, DEFAULT_PASSWORD);
  if (!s.ok) throw new Error(`login failed: status=${s.res.status} body=${s.res.body}`);
  if (!s.cookies.refresh_token || !s.cookies.device_id) throw new Error('missing refresh_token/device_id cookie');
  return { cookies: s.cookies };
}

export default function (data) {
  const headers = {
    'Content-Type': 'application/json',
    'Cookie': cookieHeader(data.cookies),
  };

  const res = http.post(`${BASE_URL}/api/auth/refresh`, null, { headers });
  const ok = res.status === 200;

  if (ok) successCount.add(1);
  else if (res.status === 401 || res.status === 400 || res.status === 409 || res.status === 429) expectedFailCount.add(1);

  // Same RT race must have only one winner. k6 cannot know the global winner during run,
  // so threshold validates count==1 after the run.
  record(res, r => r.status === 200 || r.status === 400 || r.status === 401 || r.status === 409 || r.status === 429);

  check(res, {
    'refresh response is expected status': r => [200, 400, 401, 409, 429].includes(r.status),
    'refresh does not return 500': r => r.status < 500,
  });
}

export function teardown(data) {
  // Reuse the old RT once more. It should be rejected after rotation.
  const res = http.post(`${BASE_URL}/api/auth/refresh`, null, {
    headers: { 'Cookie': cookieHeader(data.cookies) },
  });
  check(res, {
    'old refresh token is not reusable after race': r => r.status !== 200,
  });
}
