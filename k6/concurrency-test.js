/**
 * DB 비관적 락 vs Redis 분산 락 — 대용량 부하 비교 테스트
 *
 * 변경 전제:
 *   ORDER_CREATE_PER_USER: 10→500/60s (임시 상향, 테스트 후 원복)
 *
 * 실행 전 준비:
 *   UPDATE product_item SET stock = 5000 WHERE item_id = 1;
 *   Redis ratelimit 키 전체 플러시
 *
 * 실행:
 *   k6 run k6/concurrency-test.js -e ITEM_ID=1 --summary-export=k6/concurrency-result.json
 *
 * 비교 포인트:
 *   - avg / p(95) / p(99) 응답시간
 *   - 락 타임아웃(409 ORDER_013) 발생 수
 *   - 재고 정합성: 5000 - stock = db_orders
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const dbLockDuration    = new Trend('db_lock_duration',    true);
const redisLockDuration = new Trend('redis_lock_duration', true);
const dbLockSuccess     = new Counter('db_lock_success');
const dbLockTimeout     = new Counter('db_lock_timeout');    // 409 ORDER_013
const dbLockFail        = new Counter('db_lock_other_fail');
const redisLockSuccess  = new Counter('redis_lock_success');
const redisLockTimeout  = new Counter('redis_lock_timeout'); // 409 ORDER_013
const redisLockFail     = new Counter('redis_lock_other_fail');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ITEM_ID  = Number(__ENV.ITEM_ID || '1');
if (isNaN(ITEM_ID) || ITEM_ID <= 0) { throw new Error(`유효하지 않은 ITEM_ID: ${__ENV.ITEM_ID}`); }
const USER_PW  = __ENV.USER_PW || 'Test1234!';

// DEVICE_REGISTER_PER_IP: 10/600s — 9개 고정
const VU_COUNT = 9;

export const options = {
  scenarios: {
    // Phase 1: DB 비관적 락 (2분)
    db_pessimistic_lock: {
      executor: 'constant-vus',
      vus: VU_COUNT,
      duration: '2m',
      startTime: '0s',
      exec: 'testDbLock',
      tags: { lock_type: 'pessimistic' },
    },
    // Phase 2: Redis 분산 락 (2분)
    // 70s gap (2m + 70s = 3m10s): ORDER_CREATE_PER_USER 60s 윈도우 만료 보장
    redis_distributed_lock: {
      executor: 'constant-vus',
      vus: VU_COUNT,
      duration: '2m',
      startTime: '3m10s',
      exec: 'testRedisLock',
      tags: { lock_type: 'redis' },
    },
  },
  thresholds: {
    db_lock_duration:    ['p(95)<1000'],
    redis_lock_duration: ['p(95)<1000'],
  },
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function setup() {
  const tokens = [];
  for (let i = 1; i <= VU_COUNT; i++) {
    const res = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: `testbuyer${i}@test.com`, password: USER_PW }),
      { headers: JSON_HEADERS });
    const token = JSON.parse(res.body)?.data?.accessToken;
    if (!token) throw new Error(`testbuyer${i} 토큰 발급 실패`);
    tokens.push(token);
    sleep(3.5);
  }
  return { tokens };
}

const ORDER_PAYLOAD = JSON.stringify({
  orderType: 'DIRECT',
  items: [{ itemId: ITEM_ID, quantity: 1 }],
  receiverName: 'K6 락비교',
  receiverPhone: '010-0000-0000',
  receiverAddress: '서울시 강남구 테스트로 1',
  deliveryMessage: 'K6 대용량 락 비교',
  usedPoint: 0,
});

export function testDbLock(data) {
  const token = data.tokens[__VU - 1];
  const res = http.post(`${BASE_URL}/api/orders`, ORDER_PAYLOAD,
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } });

  dbLockDuration.add(res.timings.duration);
  const ok = res.status === 200 || res.status === 201;
  check(res, { 'DB락: 주문 성공': () => ok });

  if (ok) dbLockSuccess.add(1);
  else if (res.status === 409) dbLockTimeout.add(1);
  else dbLockFail.add(1);

  sleep(0.5);
}

export function testRedisLock(data) {
  const token = data.tokens[__VU - 1];
  const res = http.post(`${BASE_URL}/api/orders/redis-lock`, ORDER_PAYLOAD,
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } });

  redisLockDuration.add(res.timings.duration);
  const ok = res.status === 200 || res.status === 201;
  check(res, { 'Redis락: 주문 성공': () => ok });

  if (ok) redisLockSuccess.add(1);
  else if (res.status === 409) redisLockTimeout.add(1);
  else redisLockFail.add(1);

  sleep(0.5);
}
