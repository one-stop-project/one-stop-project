import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// ── 커스텀 메트릭 ──────────────────────────────────────────────
const loginDuration    = new Trend('login_duration',    true);
const productsDuration = new Trend('products_duration', true);
const orderDuration    = new Trend('order_duration',    true);
const suspendDuration  = new Trend('suspend_duration',  true);
const errorRate        = new Rate('error_rate');

// ── VU 수 상수 — options.vus, setup() 토큰 발급 수, seed-users.sql 계정 수와 동기화
const BUYER_VU_COUNT  = 9;
const ADMIN_VU_COUNT  = 0;

// ── 테스트 옵션 ────────────────────────────────────────────────
export const options = {
  vus: BUYER_VU_COUNT,
  duration: '5m',
  thresholds: {
    // 전체 95% 응답시간 500ms 이하
    http_req_duration: ['p(95)<500'],
    // 시나리오별 95% 응답시간 목표
    login_duration:    ['p(95)<500'],
    products_duration: ['p(95)<500'],
    order_duration:    ['p(95)<500'],
    suspend_duration:  ['p(95)<500'],
    // 에러율 1% 미만
    error_rate:        ['rate<0.01'],
  },
};

// ── 환경 설정 ──────────────────────────────────────────────────
const BASE_URL   = __ENV.BASE_URL || 'http://localhost:8080';
const USER_PW    = __ENV.USER_PW  || 'Test1234!';
// VU별 계정: testbuyer1~50@test.com, 관리자: testadmin1~5@test.com
// 계정 사전 생성 필요: k6/seed-users.sql 참고
const SELLER_RAW = __ENV.SELLER_ID;
const ITEM_RAW   = __ENV.ITEM_ID;

if (!SELLER_RAW || !ITEM_RAW) {
  throw new Error('SELLER_ID, ITEM_ID 환경변수는 필수입니다. (-e SELLER_ID=xxx -e ITEM_ID=xxx)');
}

const SELLER_ID = Number(SELLER_RAW);
const ITEM_ID   = Number(ITEM_RAW);

if (isNaN(SELLER_ID) || isNaN(ITEM_ID)) {
  throw new Error(`SELLER_ID, ITEM_ID는 숫자여야 합니다. 입력값: SELLER_ID=${SELLER_RAW}, ITEM_ID=${ITEM_RAW}`);
}

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// ── 로그인 헬퍼 ───────────────────────────────────────────────
function loginOnce(email, password) {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    { headers: JSON_HEADERS }
  );

  loginDuration.add(res.timings.duration);

  const ok = check(res, {
    'login: status 200':       (r) => r.status === 200,
    'login: accessToken 존재': (r) => {
      try { return !!JSON.parse(r.body).data?.accessToken; } catch { return false; }
    },
  });
  errorRate.add(!ok);

  if (!ok) return null;
  return JSON.parse(res.body).data.accessToken;
}

// ── setup: 테스트 시작 전 VU별 토큰 1회 발급 ─────────────────
// LOGIN_PER_IP rate limit(1분 20회) 우회 — 매 iteration 로그인 대신 토큰 재사용
// sleep 3.5s: 20req/min 기준 최소 3s 간격 + 여유 0.5s (55회 × 3.5s ≈ 3.2분)
export function setup() {
  const buyerTokens = [];
  const adminTokens = [];

  for (let i = 1; i <= BUYER_VU_COUNT; i++) {
    const token = loginOnce(`testbuyer${i}@test.com`, USER_PW);
    if (!token) throw new Error(`testbuyer${i} 토큰 발급 실패 — setup 중단`);
    buyerTokens.push(token);
    sleep(3.5);
  }

  for (let i = 1; i <= ADMIN_VU_COUNT; i++) {
    const token = loginOnce(`testadmin${i}@test.com`, USER_PW);
    if (!token) throw new Error(`testadmin${i} 토큰 발급 실패 — setup 중단`);
    adminTokens.push(token);
    sleep(3.5);
  }

  return { buyerTokens, adminTokens };
}

// ── 시나리오 2: GET /api/products ─────────────────────────────
function getProducts(token) {
  const headers = token
    ? { ...JSON_HEADERS, Authorization: `Bearer ${token}` }
    : JSON_HEADERS;

  const res = http.get(`${BASE_URL}/api/products`, { headers });

  productsDuration.add(res.timings.duration);

  const ok = check(res, {
    'products: status 200': (r) => r.status === 200,
  });
  errorRate.add(!ok);
}

// ── 시나리오 3: POST /api/orders ──────────────────────────────
function createOrder(token) {
  if (!token) return;

  const payload = JSON.stringify({
    orderType:      'DIRECT',
    items:          [{ itemId: Number(ITEM_ID), quantity: 1 }],
    receiverName:   'K6 테스터',
    receiverPhone:  '010-0000-0000',
    receiverAddress: '서울시 강남구 테스트로 1',
    deliveryMessage: 'K6 부하 테스트 주문',
    usedPoint:      0,
  });

  const res = http.post(
    `${BASE_URL}/api/orders`,
    payload,
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } }
  );

  orderDuration.add(res.timings.duration);

  const ok = check(res, {
    'order: status 200 또는 201': (r) => r.status === 200 || r.status === 201,
  });
  errorRate.add(!ok);
}

// ── 시나리오 4: PATCH /api/admin/sellers/{sellerId}/force-inactive
function forceInactiveSeller(adminToken) {
  if (!adminToken) return;

  const res = http.patch(
    `${BASE_URL}/api/admin/sellers/${SELLER_ID}/force-inactive`,
    JSON.stringify({ reason: 'K6 부하 테스트 - 판매자 강제 비활성화' }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${adminToken}` } }
  );

  suspendDuration.add(res.timings.duration);

  // 이미 정지된 경우(400)도 허용 (반복 실행 시 발생)
  const ok = check(res, {
    'suspend: status 200 또는 400': (r) => r.status === 200 || r.status === 400,
  });
  errorRate.add(!ok);
}

// ── 메인 VU 루프 ───────────────────────────────────────────────
export default function (data) {
  // setup()에서 발급한 토큰 재사용 — 반복 로그인으로 인한 rate limit 차단 방지
  const token      = data.buyerTokens[__VU - 1];
  const adminToken = ADMIN_VU_COUNT > 0 ? data.adminTokens[(__VU - 1) % ADMIN_VU_COUNT] : null;

  group('1. 상품 목록 조회', () => {
    getProducts(token);
    sleep(0.5);
  });

  group('2. 주문 생성', () => {
    createOrder(token);
    sleep(1);
  });

  // 관리자 시나리오 (10% VU만 실행)
  if (Math.random() < 0.1) {
    group('3. 관리자 - 판매자 강제 비활성화', () => {
      forceInactiveSeller(adminToken);
      sleep(1);
    });
  }

  sleep(1);
}
