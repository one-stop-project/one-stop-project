import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// ── 커스텀 메트릭 ──────────────────────────────────────────────
const loginDuration    = new Trend('login_duration',    true);
const productsDuration = new Trend('products_duration', true);
const orderDuration    = new Trend('order_duration',    true);
const suspendDuration  = new Trend('suspend_duration',  true);
const errorRate        = new Rate('error_rate');

// ── 테스트 옵션 ────────────────────────────────────────────────
export const options = {
  vus: 50,
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
const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:8080';
const USER_EMAIL  = __ENV.USER_EMAIL  || 'testuser@example.com';
const USER_PW     = __ENV.USER_PW     || 'Test1234!';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@example.com';
const ADMIN_PW    = __ENV.ADMIN_PW    || 'Admin1234!';
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

// ── 시나리오 1: POST /api/auth/login ──────────────────────────
function login(email, password) {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    { headers: JSON_HEADERS }
  );

  loginDuration.add(res.timings.duration);

  const ok = check(res, {
    'login: status 200':           (r) => r.status === 200,
    'login: accessToken 존재':     (r) => {
      try { return !!JSON.parse(r.body).data?.accessToken; } catch { return false; }
    },
  });
  errorRate.add(!ok);

  if (!ok) return null;
  return JSON.parse(res.body).data.accessToken;
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
export default function () {
  group('1. 로그인', () => {
    const token = login(USER_EMAIL, USER_PW);

    group('2. 상품 목록 조회', () => {
      getProducts(token);
      sleep(0.5);
    });

    group('3. 주문 생성', () => {
      createOrder(token);
      sleep(1);
    });
  });

  // 관리자 시나리오 (10% VU만 실행)
  if (Math.random() < 0.1) {
    group('4. 관리자 - 판매자 강제 비활성화', () => {
      const adminToken = login(ADMIN_EMAIL, ADMIN_PW);
      forceInactiveSeller(adminToken);
      sleep(1);
    });
  }

  sleep(1);
}
