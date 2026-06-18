# OneStop 로컬 k6 부하테스트

## 목적

운영 최대 TPS 산정이 아니라 동일한 로컬 환경에서 개선 전후 상대 성능과 동시성·정합성을 검증합니다.

## 구성

| 스크립트 | 검증 대상 | 데이터 변경 |
|---|---|---|
| `01-product-cache.js` | 상품 목록 캐시 전후 | 없음 |
| `02-login-rate-limit.js` | 로그인 Rate Limit | Redis 키·로그인 기록 |
| `03-refresh-token-race.js` | 동일 RT 동시 Rotation | RT 교체 |
| `04-coupon-first-come.js` | 선착순 쿠폰 발급 | 쿠폰 발급 |
| `05-order-payment.js` | 주문 생성→결제 승인 | 주문·결제·재고 |
| `06-point-concurrency.js` | 동일 사용자 포인트 경합 | 결제·포인트 |
| `07-sse-connect.js` | SSE 동시 연결 | 연결 상태 |

## 공통 준비

- local 프로파일 애플리케이션 실행
- MySQL, Redis, Kafka 실행
- 테스트 전용 BUYER 계정 사용
- APPROVED 상품, ON_SALE 옵션과 충분한 재고 준비
- 데이터 변경 테스트 전에 DB 백업 또는 초기화 SQL 준비
- 운영 DB에서는 실행 금지

```bash
export BASE_URL=http://localhost:8080
```

API 경로가 코드와 다르면 환경변수로 변경합니다.

```bash
-e PRODUCTS_PATH='/api/products' \
-e COUPON_ISSUE_PATH='/실제/쿠폰경로/{couponId}' \
-e ORDER_CREATE_PATH='/실제/주문경로' \
-e PAYMENT_APPROVE_PATH='/실제/결제승인경로'
```

## 1. 상품 목록 캐시 전후

```bash
k6 run -e BASE_URL=http://localhost:8080 -e RATE=30 -e DURATION=1m scripts/01-product-cache.js
```

비교 절차:

1. Redis 상품 캐시 삭제 후 Cold 실행
2. 동일 요청으로 cache warm-up
3. 같은 rate와 duration으로 Warm 실행
4. P50/P95/P99, 처리량, DB Query 수 비교

## 2. 로그인 Rate Limit

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e EMAIL=buyer@test.com \
  -e PASSWORD='Password1!' \
  -e VUS=1 \
  -e ITERATIONS=8 \
  scripts/02-login-rate-limit.js
```

계정 제한이 1분 5회라면 같은 계정·기기 조건에서 초기 허용 후 429가 발생해야 합니다. 테스트 사이에는 Rate Limit 키를 초기화하거나 윈도우 만료를 기다립니다.

## 3. Refresh Token Rotation 동시 요청

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e EMAIL=buyer@test.com \
  -e PASSWORD='Password1!' \
  -e VUS=10 \
  -e ITERATIONS=10 \
  scripts/03-refresh-token-race.js
```

합격 기준:

- 동일 RT를 재사용한 요청 중 성공 정확히 1건
- 나머지는 400/401/409 등 제어된 실패
- 500 응답 0건
- Redis 최종 RT 값 1개

현재 스크립트는 쿠키 기반 RT와 `device_id` 정책을 전제로 합니다. Refresh 요청 Body가 필요하면 `REFRESH_BODY`를 지정합니다.

## 4. 쿠폰 선착순 발급

서로 다른 BUYER의 Access Token이 필요합니다.

```bash
export ACCESS_TOKENS_JSON='["TOKEN1","TOKEN2","TOKEN3"]'

k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e COUPON_ID=1 \
  -e ACCESS_TOKENS_JSON="$ACCESS_TOKENS_JSON" \
  -e VUS=100 \
  scripts/04-coupon-first-come.js
```

합격 기준:

- 성공 건수 ≤ 쿠폰 총 수량
- 동일 사용자 중복 발급 0건
- `issued_quantity`와 실제 `user_coupon` 수 일치
- Redis 재고 음수 없음
- 500 응답 0건

## 5. 주문 생성·결제 승인

현재 API DTO를 그대로 환경변수 JSON으로 주입하도록 만들었습니다.

```bash
export ORDER_USERS_JSON='[{"accessToken":"TOKEN1"},{"accessToken":"TOKEN2"}]'
export ORDER_BODY_JSON='{
  "itemId":1,
  "quantity":1,
  "usedPoint":0,
  "receiverName":"k6구매자",
  "receiverPhone":"010-0000-0000",
  "receiverAddress":"서울시 테스트구",
  "deliveryMessage":"k6 load test",
  "orderType":"DIRECT"
}'
export PAYMENT_BODY_TEMPLATE_JSON='{"amount":10000}'

k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e ORDER_USERS_JSON="$ORDER_USERS_JSON" \
  -e ORDER_BODY_JSON="$ORDER_BODY_JSON" \
  -e PAYMENT_BODY_TEMPLATE_JSON="$PAYMENT_BODY_TEMPLATE_JSON" \
  -e VUS=10 \
  -e DURATION=30s \
  scripts/05-order-payment.js
```

합격 기준:

- 재고 음수 0
- 주문당 Payment 최대 1건
- 결제 성공 주문만 PAID
- 실패 요청에서 부분 반영 없음
- P95/P99 및 HikariCP 사용량 기록

## 6. 포인트 동시 차감

동일 BUYER가 소유한 서로 다른 `PENDING_PAYMENT` 주문을 준비하고, 각 주문에 사용 포인트를 설정합니다. 주문들의 요구 포인트 합계가 현재 잔액을 초과하도록 구성합니다.

```bash
export ACCESS_TOKEN='SAME_BUYER_TOKEN'
export POINT_ORDER_IDS_JSON='[101,102,103,104,105]'
export PAYMENT_BODY_TEMPLATE_JSON='{"amount":10000}'

k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e ACCESS_TOKEN="$ACCESS_TOKEN" \
  -e POINT_ORDER_IDS_JSON="$POINT_ORDER_IDS_JSON" \
  -e PAYMENT_BODY_TEMPLATE_JSON="$PAYMENT_BODY_TEMPLATE_JSON" \
  -e VUS=5 \
  scripts/06-point-concurrency.js
```

합격 기준:

- Point.balance 음수 없음
- Point.balance = 사용 가능한 PointHistory.remainingAmount 합계
- 성공 결제에 대해서만 USE 이력 생성
- 원본 remainingAmount 음수 없음
- 충돌은 제어된 응답이며 500은 0건

## 7. SSE 동시 연결

SSE 스크립트는 `xk6-sse`가 포함된 k6 빌드가 필요합니다.

```bash
xk6 build --with github.com/phymbert/xk6-sse
```

서로 다른 사용자의 토큰을 사용해야 합니다. 현재 정책은 사용자당 서버 인스턴스 기준 emitter 1개이므로 같은 Token을 반복하면 기존 연결이 교체됩니다.

```bash
export ACCESS_TOKENS_JSON='["TOKEN1","TOKEN2","TOKEN3"]'

./k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e ACCESS_TOKENS_JSON="$ACCESS_TOKENS_JSON" \
  -e VUS=20 \
  -e SSE_TIMEOUT=30s \
  scripts/07-sse-connect.js
```

합격 기준:

- SSE handshake 성공률
- P95 연결 시간
- 연결 유지 오류 수
- 알림 이벤트 수신 수
- 종료 후 emitter 정리
- 재연결 시 기존 emitter 교체

## 권장 테스트 단계

1. Smoke: VU 1~2, 10~30초
2. Load: VU 10→50, 1분
3. Stress: VU 50→100 이상
4. Spike: 짧은 시간에 급증
5. 같은 조건으로 3회 반복

## 반드시 기록할 환경

- Git commit SHA
- CPU/RAM
- JVM Heap
- DB/Redis/Kafka 실행 방식
- 테스트 데이터 건수
- VU, rate, duration
- P50/P95/P99
- 처리량과 오류율
- DB Connection Pool
- Redis latency와 command 수
- 테스트 후 정합성 검증 결과

## 해석 주의

로컬 결과는 운영 최대 TPS가 아닙니다. 동일 환경에서 개선 전후 비교와 정합성 보장 여부를 검증한 결과로만 사용합니다.
