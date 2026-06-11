/**
 * 쿠폰 발급 3전략 — 1000 VU 동시성 + 정합성 비교 테스트
 *
 * 전략:
 *   decr : Redis DECR + Lua 안전 차감
 *   lua  : Lua Script 원자 처리 (중복확인+차감+기록)
 *   lock : Redisson 분산 락
 *
 * 실행 전 DB 준비:
 *   -- 각 전략별 쿠폰 3개 생성 (totalQuantity=500, status=ACTIVE, expiredAt=미래)
 *   -- COUPON_DECR_ID, COUPON_LUA_ID, COUPON_LOCK_ID 를 환경변수로 전달
 *   SOURCE k6/seed-users.sql;
 *   SOURCE k6/seed-users-1000.sql;
 *
 * 실행:
 *   k6 run k6/coupon-test.js \
 *     -e COUPON_DECR_ID=1 \
 *     -e COUPON_LUA_ID=2 \
 *     -e COUPON_LOCK_ID=3 \
 *     --summary-export=k6/coupon-result.json
 *
 * 정합성 검증 (테스트 후):
 *   SELECT issued_quantity FROM coupons WHERE coupon_id IN (1, 2, 3);
 *   SELECT COUNT(*) FROM user_coupons WHERE coupon_id = 1;  -- issued_quantity와 일치해야 함
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const decrDuration = new Trend('coupon_decr_duration', true);
const luaDuration  = new Trend('coupon_lua_duration',  true);
const lockDuration = new Trend('coupon_lock_duration', true);

const decrSuccess  = new Counter('coupon_decr_success');
const luaSuccess   = new Counter('coupon_lua_success');
const lockSuccess  = new Counter('coupon_lock_success');

const decrSoldOut  = new Counter('coupon_decr_sold_out');
const luaSoldOut   = new Counter('coupon_lua_sold_out');
const lockSoldOut  = new Counter('coupon_lock_sold_out');

const decrFail     = new Counter('coupon_decr_other_fail');
const luaFail      = new Counter('coupon_lua_other_fail');
const lockFail     = new Counter('coupon_lock_other_fail');

const BASE_URL        = __ENV.BASE_URL        || 'http://54.116.46.196';
const COUPON_DECR_ID  = Number(__ENV.COUPON_DECR_ID  || '1');
const COUPON_LUA_ID   = Number(__ENV.COUPON_LUA_ID   || '2');
const COUPON_LOCK_ID  = Number(__ENV.COUPON_LOCK_ID  || '3');
const USER_PW         = __ENV.USER_PW         || 'Test1234!';

const VU_COUNT = 1000;

export const options = {
  setupTimeout: '5m',
  scenarios: {
    // Phase 1: Redis DECR 전략
    coupon_decr: {
      executor: 'constant-vus',
      vus: VU_COUNT,
      duration: '30s',
      startTime: '0s',
      exec: 'testDecr',
      tags: { strategy: 'decr' },
    },
    // Phase 2: Lua Script 전략 (40s gap — Redis issued-users Set 키 간섭 없애기)
    coupon_lua: {
      executor: 'constant-vus',
      vus: VU_COUNT,
      duration: '30s',
      startTime: '1m10s',
      exec: 'testLua',
      tags: { strategy: 'lua' },
    },
    // Phase 3: Redisson 분산 락 전략
    coupon_lock: {
      executor: 'constant-vus',
      vus: VU_COUNT,
      duration: '30s',
      startTime: '2m20s',
      exec: 'testLock',
      tags: { strategy: 'lock' },
    },
  },
  thresholds: {
    coupon_decr_duration: ['p(95)<2000'],
    coupon_lua_duration:  ['p(95)<2000'],
    coupon_lock_duration: ['p(95)<2000'],
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
    if (!token) throw new Error(`testbuyer${i} 토큰 발급 실패 (status=${res.status})`);
    tokens.push(token);
  }
  return { tokens };
}

function issueAndRecord(url, token, durationMetric, successMetric, soldOutMetric, failMetric) {
  const res = http.post(url, null, {
    headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` },
  });

  durationMetric.add(res.timings.duration);

  const body = JSON.parse(res.body || '{}');
  const errorCode = body?.error?.code || '';

  if (res.status === 200) {
    successMetric.add(1);
    check(res, { '쿠폰 발급 성공': () => true });
  } else if (res.status === 409 && (errorCode === 'COUPON_001' || errorCode === 'COUPON_002')) {
    // COUPON_001 = 수량 소진, COUPON_002 = 중복 발급
    soldOutMetric.add(1);
    check(res, { '쿠폰 발급 성공': () => false });
  } else {
    failMetric.add(1);
    check(res, { '쿠폰 발급 성공': () => false });
  }
}

export function testDecr(data) {
  const token = data.tokens[__VU - 1];
  issueAndRecord(
    `${BASE_URL}/api/test/coupons/${COUPON_DECR_ID}/issue/decr`,
    token,
    decrDuration, decrSuccess, decrSoldOut, decrFail
  );
}

export function testLua(data) {
  const token = data.tokens[__VU - 1];
  issueAndRecord(
    `${BASE_URL}/api/test/coupons/${COUPON_LUA_ID}/issue/lua`,
    token,
    luaDuration, luaSuccess, luaSoldOut, luaFail
  );
}

export function testLock(data) {
  const token = data.tokens[__VU - 1];
  issueAndRecord(
    `${BASE_URL}/api/test/coupons/${COUPON_LOCK_ID}/issue/lock`,
    token,
    lockDuration, lockSuccess, lockSoldOut, lockFail
  );
}
