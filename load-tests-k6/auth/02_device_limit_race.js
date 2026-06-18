import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { USER_EMAIL, DEFAULT_PASSWORD, login, randomDeviceId, record, thresholdBase } from '../lib/common.js';

const loginSuccess = new Counter('device_login_success_count');
const loginBlocked = new Counter('device_login_blocked_count');

export const options = {
  scenarios: {
    device_limit_race: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 10),
      iterations: Number(__ENV.ITERATIONS || 10),
      maxDuration: __ENV.MAX_DURATION || '30s',
    },
  },
  thresholds: Object.assign(thresholdBase(), {
    server_error_rate: ['rate<0.01'],
  }),
};

export default function () {
  const deviceId = randomDeviceId('k6-device-limit');
  const s = login(USER_EMAIL, DEFAULT_PASSWORD, { 'User-Agent': `k6-device-limit/${deviceId}` }, deviceId);

  if (s.res.status === 200) loginSuccess.add(1);
  if (s.res.status === 429) loginBlocked.add(1);

  record(s.res, r => r.status === 200 || r.status === 429 || r.status === 401);

  check(s.res, {
    'login expected status': r => [200, 401, 429].includes(r.status),
    'no server error': r => r.status < 500,
  });
}

export function handleSummary(data) {
  return {
    [__ENV.SUMMARY_FILE || 'results/02_device_limit_race-summary.json']: JSON.stringify(data, null, 2),
    stdout: `\nDevice limit race finished. Verify Redis manually:\n` +
      `  redis-cli ZCARD devices:{userId}       # expected <= 5\n` +
      `  redis-cli KEYS 'refresh:{userId}:*'   # expected <= 5\n\n`,
  };
}
