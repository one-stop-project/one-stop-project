import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, USER_EMAIL, jsonHeaders, record, blockedCounter, thresholdBase } from '../lib/common.js';

const spoofedRequests = new Counter('spoofed_xff_requests');

export const options = {
  scenarios: {
    spoofed_xff_login_abuse: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 30),
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 60),
      maxVUs: Number(__ENV.MAX_VUS || 200),
    },
  },
  thresholds: Object.assign(thresholdBase(), {
    // If every spoofed XFF bypasses IP-based limit, blocked_count may stay 0.
    // This threshold intentionally fails to expose a proxy/XFF trust issue.
    blocked_count: ['count>0'],
    server_error_rate: ['rate<0.01'],
  }),
};

export default function () {
  const email = __ENV.RATE_LIMIT_EMAIL || USER_EMAIL;
  const wrongPassword = __ENV.WRONG_PASSWORD || 'Wrong1234!';
  const fakeIp = `203.0.113.${(__VU + __ITER) % 250}`;
  spoofedRequests.add(1);

  const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ email, password: wrongPassword }), {
    headers: jsonHeaders({
      'User-Agent': 'k6-ip-spoof-test',
      'X-Forwarded-For': `${fakeIp}, 10.0.0.1`,
      'X-Real-IP': fakeIp,
    }),
  });

  if (res.status === 429 || res.status === 423 || res.status === 403) blockedCounter.add(1);
  record(res, r => [200, 400, 401, 403, 423, 429].includes(r.status));

  check(res, {
    'spoof test expected status': r => [200, 400, 401, 403, 423, 429].includes(r.status),
    'no server error': r => r.status < 500,
  });
}
