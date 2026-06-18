import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const users = new SharedArray('refresh users', function () {
  return JSON.parse(open('../data/login-users.json'));
});

const loginSuccess = new Counter('setup_login_success');
const refreshSuccess = new Counter('refresh_success');
const refreshUnauthorized = new Counter('refresh_unauthorized');
const refreshLimited = new Counter('refresh_limited');
const refreshUnexpected = new Rate('refresh_unexpected');

const loginLatency = new Trend('setup_login_latency', true);
const refreshLatency = new Trend('refresh_latency', true);

export const options = {
  scenarios: {
    refresh_load: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.VUS || 2),
      iterations: Number(__ENV.ITERATIONS || 1),
      maxDuration: '1m',
    },
  },
  thresholds: {
    refresh_unexpected: ['rate<0.01'],
    refresh_latency: ['p(95)<2000'],
  },
};

function createDeviceId(vu) {
  return `44444444-4444-4444-8444-${String(vu).padStart(12, '0')}`;
}

export default function () {
  if (users.length === 0) {
    throw new Error('login-users.json is empty');
  }

  const user = users[(__VU - 1) % users.length];
  const deviceId = createDeviceId(__VU);

  const jar = http.cookieJar();
  jar.set(BASE_URL, 'device_id', deviceId, {
    path: '/',
  });

  // 각 VU가 독립된 계정과 RT를 준비한다.
  const loginResponse = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({
      email: user.email,
      password: user.password,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': `k6-refresh-load/vu-${__VU}`,
      },
      tags: {
        name: 'POST /api/auth/login refresh-setup',
      },
    },
  );

  loginLatency.add(loginResponse.timings.duration);

  const loginOk = check(loginResponse, {
    'setup login is 2xx': (r) =>
      r.status >= 200 && r.status < 300,
  });

  if (!loginOk) {
    if (loginResponse.status === 429) {
      refreshLimited.add(1);
    } else {
      refreshUnexpected.add(true);
    }
    return;
  }

  loginSuccess.add(1);

  // refresh_token의 Path가 /api/auth이므로
  // 해당 경로로 요청하면 k6 CookieJar가 자동 첨부한다.
  const refreshResponse = http.post(
    `${BASE_URL}/api/auth/refresh`,
    '{}',
    {
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': `k6-refresh-load/vu-${__VU}`,
      },
      tags: {
        name: 'POST /api/auth/refresh normal-load',
      },
    },
  );

  refreshLatency.add(refreshResponse.timings.duration);

  if (
    refreshResponse.status >= 200
    && refreshResponse.status < 300
  ) {
    refreshSuccess.add(1);
  } else if (refreshResponse.status === 401) {
    refreshUnauthorized.add(1);
  } else if (refreshResponse.status === 429) {
    refreshLimited.add(1);
  } else {
    refreshUnexpected.add(true);
  }

  check(refreshResponse, {
    'refresh status is 2xx': (r) =>
      r.status >= 200 && r.status < 300,

    'refresh response is JSON': (r) =>
      (r.headers['Content-Type'] || '')
        .includes('application/json'),
  });
}

export function handleSummary(data) {
  const count = (name) =>
    data.metrics[name]?.values?.count ?? 0;

  const rate = (name) =>
    data.metrics[name]?.values?.rate ?? 0;

  const refreshP95 =
    data.metrics.refresh_latency?.values?.['p(95)'] ?? 0;

  const loginP95 =
    data.metrics.setup_login_latency?.values?.['p(95)'] ?? 0;

  return {
    stdout:
      '\n정상 Refresh 부하테스트 요약\n'
      + `- 준비 로그인 성공: ${count('setup_login_success')}\n`
      + `- Refresh 성공: ${count('refresh_success')}\n`
      + `- Refresh 401: ${count('refresh_unauthorized')}\n`
      + `- 429: ${count('refresh_limited')}\n`
      + `- 예상 밖 오류율: ${rate('refresh_unexpected')}\n`
      + `- 로그인 P95: ${loginP95}ms\n`
      + `- Refresh P95: ${refreshP95}ms\n\n`,
  };
}
