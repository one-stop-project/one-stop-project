import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const users = new SharedArray('login users', function () {
  return JSON.parse(
    open('../data/login-users.json')
  );
});

const loginSuccess = new Counter('login_success');
const loginUnauthorized = new Counter('login_unauthorized');
const loginLimited = new Counter('login_limited');
const loginUnexpected = new Rate('login_unexpected');
const loginLatency = new Trend('login_latency', true);

export const options = {
  scenarios: {
    login_load: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.VUS || 2),
      iterations: Number(__ENV.ITERATIONS || 1),
      maxDuration: '1m',
    },
  },
  thresholds: {
    login_unexpected: ['rate<0.01'],
    login_latency: ['p(95)<5000'],
  },
};

function createDeviceId(vu) {
  return `33333333-3333-4333-8333-${String(vu).padStart(12, '0')}`;
}

export default function () {
  if (users.length === 0) {
    throw new Error('login-users.json is empty');
  }

  const user = users[(__VU - 1) % users.length];
  const deviceId = createDeviceId(__VU);

  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({
      email: user.email,
      password: user.password,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': `k6-login-load/vu-${__VU}`,
        Cookie: `device_id=${deviceId}`,
      },
      tags: {
        name: 'POST /api/auth/login normal-load',
      },
    },
  );

  loginLatency.add(response.timings.duration);

  if (response.status >= 200 && response.status < 300) {
    loginSuccess.add(1);
  } else if (response.status === 401) {
    loginUnauthorized.add(1);
  } else if (response.status === 429) {
    loginLimited.add(1);
  } else {
    loginUnexpected.add(true);
  }

  check(response, {
    'login status is 2xx': (r) =>
      r.status >= 200 && r.status < 300,

    'response is JSON': (r) =>
      (r.headers['Content-Type'] || '')
        .includes('application/json'),
  });

  sleep(Number(__ENV.SLEEP || 0));
}

export function handleSummary(data) {
  const count = (name) =>
    data.metrics[name]?.values?.count ?? 0;

  const rate = (name) =>
    data.metrics[name]?.values?.rate ?? 0;

  const p95 =
    data.metrics.login_latency?.values?.['p(95)'] ?? 0;

  return {
    stdout:
      '\n정상 로그인 부하테스트 요약\n'
      + `- 성공: ${count('login_success')}\n`
      + `- 401: ${count('login_unauthorized')}\n`
      + `- 429: ${count('login_limited')}\n`
      + `- 예상 밖 오류율: ${rate('login_unexpected')}\n`
      + `- P95: ${p95}ms\n\n`,
  };
}
