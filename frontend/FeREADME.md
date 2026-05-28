# 🛍️ One-Stop Frontend

> 진심을 담은 쇼핑, One-Stop 이커머스 프론트엔드
> React 18 + TypeScript + Vite + TanStack Query

---

## 🚀 시작하기

```bash
# 1. 의존성 설치
npm install

# 2. 환경 변수 설정
cp .env.example .env

# 3. 개발 서버 실행
npm run dev
# → http://localhost:3000

# 4. 프로덕션 빌드
npm run build
```

### 백엔드 연동

개발 시 `vite.config.ts`의 proxy가 `/api` 요청을 `http://localhost:8080`으로 전달합니다.
백엔드 서버를 먼저 실행해주세요.

---

## 📁 프로젝트 구조

```
src/
├── api/
│   └── client.ts              # axios + JWT 인터셉터 + 자동 refresh
├── store/
│   └── useAuthStore.ts        # zustand 인증 전역 상태
├── types/
│   └── common.ts              # 백엔드 enum 매칭 타입
├── utils/
│   └── format.ts              # 가격/날짜/전화번호 포맷
├── domains/                   # 도메인별 API + 타입 (Step 1)
│   ├── auth/authApi.ts
│   ├── user/userApi.ts
│   ├── product/productApi.ts
│   ├── cart/cartApi.ts
│   ├── order/orderApi.ts
│   ├── payment/paymentApi.ts
│   ├── delivery/deliveryApi.ts
│   ├── seller/sellerApi.ts
│   └── admin/adminApi.ts
├── hooks/queries/             # React Query 훅 (Step 2)
│   ├── useAuthQuery.ts
│   ├── useUserQuery.ts
│   ├── useProductQuery.ts
│   ├── useCartQuery.ts        # 낙관적 업데이트 포함
│   ├── useOrderQuery.ts
│   ├── usePaymentQuery.ts
│   ├── useDeliveryQuery.ts
│   ├── useSellerQuery.ts
│   └── useAdminQuery.ts
├── components/
│   ├── common/                # Spinner, EmptyState, Header
│   ├── layout/                # Main/Seller/Admin 레이아웃
│   ├── route/                 # ProtectedRoute, AuthInitializer
│   └── product/               # ProductCard
└── pages/                     # UI 페이지 (Step 3)
    ├── auth/                  # 로그인, 회원가입
    ├── product/               # 홈, 목록, 상세
    ├── cart/                  # 장바구니
    ├── order/                 # 주문/결제/완료/상세
    ├── payment/               # 결제
    ├── user/                  # 마이페이지, 비번변경, 탈퇴
    ├── seller/                # 판매자 대시보드/상품/재고/주문
    └── admin/                 # 관리자 대시보드/상품/판매자/주문
```

---

## 🔐 인증 아키텍처

### 토큰 전략

```
Access Token (AT):
- 메모리에만 저장 (XSS 방어)
- Authorization: Bearer 헤더로 전송
- 15분 만료

Refresh Token (RT):
- HttpOnly 쿠키 (JS 접근 불가)
- withCredentials로 자동 전송
- 14일 만료
```

### 자동 토큰 갱신

`api/client.ts`의 인터셉터가 처리:

1. 모든 요청에 AT 자동 주입
2. 401 응답 시 자동으로 `/auth/refresh` 호출
3. 동시 요청 시 큐로 관리 (중복 refresh 방지)
4. refresh 실패 시 로그인 페이지로

### 세션 복구

`AuthInitializer`가 앱 시작 시:
- AT는 메모리라 새로고침하면 사라짐
- RT 쿠키로 refresh 시도 → 성공하면 세션 복구

---

## 🎨 디자인 시스템

### 컬러

```
primary: red 계열 (#dc2626) — 쇼핑몰 포인트 컬러
gray: 중립 톤
```

### 폰트

Pretendard (CDN 로드)

### 공통 클래스 (index.css)

```
.btn-primary    # 주요 액션 버튼
.btn-secondary  # 보조 버튼
.input-field    # 입력 필드
.card           # 카드 컨테이너
```

---

## 🔄 상태 관리 전략

```
서버 상태 → TanStack Query
- 캐싱, 자동 refetch, 낙관적 업데이트

전역 클라이언트 상태 → Zustand
- 인증 정보 (user, isAuthenticated)

로컬 상태 → useState
- 폼 입력, UI 토글
```

### 낙관적 업데이트 예시

장바구니 수량 변경 (`useCartQuery.ts`):
- 즉시 UI 반영
- 서버 응답 대기
- 실패 시 롤백

---

## 🛣️ 라우팅

```
공개:
  /                       홈
  /products               상품 목록
  /products/:id           상품 상세
  /login, /signup         인증

구매자 (인증 필요):
  /cart                   장바구니
  /checkout               주문서
  /payment/:id            결제
  /orders                 주문 내역
  /orders/:id             주문 상세 (배송 추적)
  /mypage                 마이페이지

판매자 (SELLER):
  /seller                 대시보드
  /seller/products        상품 관리
  /seller/products/new    상품 등록
  /seller/inventory       재고 관리
  /seller/orders          주문 관리

관리자 (ADMIN):
  /admin                  대시보드
  /admin/products         상품 승인/반려
  /admin/sellers          판매자 승인
  /admin/orders           전체 주문
```

---

## 📡 API 연동 현황

모든 백엔드 컨트롤러와 1:1 매칭:

| 도메인 | 백엔드 컨트롤러 | 상태 |
|---|---|---|
| Auth | AuthController | ✅ |
| User | UserController | ✅ |
| Product | BuyerProductController, CategoryController | ✅ |
| Cart | CartController | ✅ |
| Order | OrderController | ✅ |
| Payment | PaymentController | ✅ |
| Delivery | DeliveryController | ✅ |
| Seller | SellerProduct/Item/DeliveryController | ✅ |
| Admin | AdminDashboard/Product/Seller/OrderController | ✅ |

---

## 🧩 기술 스택

| 분류 | 기술 |
|---|---|
| 프레임워크 | React 18 |
| 언어 | TypeScript |
| 빌드 | Vite |
| 스타일 | Tailwind CSS |
| 서버 상태 | TanStack Query v5 |
| 전역 상태 | Zustand |
| HTTP | Axios |
| 라우팅 | React Router v6 |
| 알림 | react-hot-toast |
| 아이콘 | lucide-react |

---

<p align="center">
  <i>진심을 담은 쇼핑, One-Stop</i><br>
  <sub>© 2026 One-Stop</sub>
</p>
