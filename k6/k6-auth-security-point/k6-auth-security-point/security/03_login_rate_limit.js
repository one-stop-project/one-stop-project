import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, USER_EMAIL, jsonHeaders, record, blockedCounter, thresholdBase } from '../lib/common.js';

const allowedCount = new Counter('login_allowed_count');
const authFailCount = new Counter('login_auth_fail_count');

export const options = {
  scenarios: {
    login_abuse_same_account: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 50),
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 100),
      maxVUs: Number(__ENV.MAX_VUS || 300),
    },
  },
  thresholds: Object.assign(thresholdBase(), {
    blocked_count: ['count>0'],
    server_error_rate: ['rate<0.01'],
  }),
};

export default function () {
  const email = __ENV.RATE_LIMIT_EMAIL || USER_EMAIL;
  const wrongPassword = __ENV.WRONG_PASSWORD || 'Wrong1234!';

  const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email, password: wrongPassword }), {
    headers: jsonHeaders({ 'User-Agent': 'k6-login-rate-limit' }),
  });

  if (res.status === 429 || res.status === 423 || res.status === 403) blockedCounter.add(1);
  else if (res.status === 400 || res.status === 401) authFailCount.add(1);
  else if (res.status === 200) allowedCount.add(1);

  record(res, r => [200, 400, 401, 403, 423, 429].includes(r.status));

  check(res, {
    'login abuse expected status': r => [200, 400, 401, 403, 423, 429].includes(r.status),
    'no server error': r => r.status < 500,
  });
}
