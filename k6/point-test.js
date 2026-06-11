/**
 * 포인트 낙관적 락(@Version) + @Retryable 정합성 테스트
 *
 * 시나리오:
 *   - testbuyer1 계정 1개를 1000 VU가 동시에 100pt 차감 시도
 *   - @Version 충돌 시 @Retryable 최대 5회 재시도
 *   - 최종 잔액이 = 초기잔액 - (성공건수 × 100) 이면 정합성 OK
 *   - 잔액이 음수거나 성공건수 > 초기잔액/100 이면 정합성 깨진 것
 *
 * 실행 전 준비:
 *   -- testbuyer1 포인트 충전 (500 VU × 100pt = 50000pt 이상 필요)
 *   -- 또는 k6 setup()에서 충전 API 호출 (아래 코드에서 처리)
 *
 * 실행:
 *   k6 run k6/point-test.js --summary-export=k6/point-result.json
 *
 * 정합성 검증 (테스트 후):
 *   SELECT balance FROM points WHERE user_id = (SELECT id FROM users WHERE email = 'testbuyer1@test.com');
 *   -- expected: INITIAL_BALANCE - (success_count × 100)
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const deductDuration   = new Trend('point_deduct_duration', true);
const deductSuccess    = new Counter('point_deduct_success');
const deductInsufficient = new Counter('point_deduct_insufficient'); // 잔액 부족
const deductRetryFail  = new Counter('point_deduct_retry_fail');   // 재시도 5회 후 실패
const deductOtherFail  = new Counter('point_deduct_other_fail');

const BASE_URL     = __ENV.BASE_URL  || 'http://54.116.46.196';
const TEST_EMAIL   = 'testbuyer1@test.com';
const USER_PW      = __ENV.USER_PW   || 'Test1234!';
const DEDUCT_AMOUNT = 100;

// 1000 VU 동시 차감 — 배포 서버 스케일
const VU_COUNT = 1000;
// 초기 잔액: 1000 VU × 100pt × 2배 여유 = 200,000pt
const INITIAL_CHARGE = 200_000;

export const options = {
  scenarios: {
    point_optimistic_lock: {
      executor: 'shared-iterations', // 각 VU 1회씩, 총 VU_COUNT회
      iterations: VU_COUNT,
      vus: VU_COUNT,
      maxDuration: '3m',
    },
  },
  thresholds: {
    point_deduct_duration: ['p(95)<3000'],
  },
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function setup() {
  // testbuyer1 로그인
  const loginRes = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: TEST_EMAIL, password: USER_PW }),
    { headers: JSON_HEADERS });
  const token = JSON.parse(loginRes.body)?.data?.accessToken;
  if (!token) throw new Error(`testbuyer1 로그인 실패 (status=${loginRes.status})`);

  // 포인트 초기화: 100,000pt 충전 (1회)
  // 충전 API: POST /api/users/me/points/charge {"amount": 100000}
  const chargeRes = http.post(`${BASE_URL}/api/users/me/points/charge`,
    JSON.stringify({ amount: INITIAL_CHARGE }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } });

  if (chargeRes.status !== 200) {
    throw new Error(`포인트 충전 실패 (status=${chargeRes.status}): ${chargeRes.body}`);
  }

  const balance = JSON.parse(chargeRes.body)?.data?.balance;
  console.log(`[setup] testbuyer1 포인트 충전 완료: 충전 후 잔액 = ${balance}pt`);

  return { token, initialBalance: balance };
}

export default function (data) {
  const res = http.post(
    `${BASE_URL}/api/users/me/points/test/deduct?amount=${DEDUCT_AMOUNT}`,
    null,
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${data.token}` } }
  );

  deductDuration.add(res.timings.duration);

  const body = JSON.parse(res.body || '{}');
  const errorCode = body?.error?.code || '';

  if (res.status === 200) {
    deductSuccess.add(1);
    check(res, { '포인트 차감 성공': () => true });
  } else if (res.status === 400 && errorCode === 'POINT_002') {
    deductInsufficient.add(1);
    check(res, { '포인트 차감 성공': () => false });
  } else if (res.status === 500 && errorCode === 'POINT_005') {
    deductRetryFail.add(1);
    check(res, { '포인트 차감 성공': () => false });
  } else {
    deductOtherFail.add(1);
    check(res, { '포인트 차감 성공': () => false });
  }
}

export function handleSummary(data) {
  const success = data.metrics['point_deduct_success']?.values?.count || 0;
  const insufficient = data.metrics['point_deduct_insufficient']?.values?.count || 0;
  const retryFail = data.metrics['point_deduct_retry_fail']?.values?.count || 0;
  const otherFail = data.metrics['point_deduct_other_fail']?.values?.count || 0;

  const expectedFinalBalance = `초기잔액 - (${success} × ${DEDUCT_AMOUNT}) = 초기잔액 - ${success * DEDUCT_AMOUNT}`;

  console.log('\n========== 포인트 낙관적 락 테스트 결과 ==========');
  console.log(`성공:       ${success}건`);
  console.log(`잔액부족:   ${insufficient}건`);
  console.log(`재시도실패: ${retryFail}건`);
  console.log(`기타실패:   ${otherFail}건`);
  console.log(`예상 최종잔액: ${expectedFinalBalance}`);
  console.log('\n정합성 검증 SQL:');
  console.log(`SELECT balance FROM points WHERE user_id = (SELECT id FROM users WHERE email = '${TEST_EMAIL}');`);
  console.log(`-- 기대값: 초기잔액 - ${success * DEDUCT_AMOUNT}`);
  console.log('===================================================\n');

  return {};
}
