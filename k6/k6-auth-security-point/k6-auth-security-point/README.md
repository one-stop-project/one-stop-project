# Auth / Security / Point k6 Load & Consistency Tests

이 k6 세트는 One-Stop 프로젝트의 Auth/Security/Point 구조 선택 근거를 검증하기 위한 테스트입니다.

## 테스트 목록

| 파일 | 목적 |
|---|---|
| `auth/01_refresh_race.js` | 동일 RT 동시 재발급 시 Redis CAS Lua / RTR 검증 |
| `auth/02_device_limit_race.js` | 동일 계정 다중 device_id 동시 로그인 시 최대 5대 정책 검증 |
| `security/03_login_rate_limit.js` | 동일 계정/동일 IP 로그인 남용 차단 검증 |
| `security/04_ip_spoofing_ratelimit.js` | X-Forwarded-For 조작으로 Rate Limit 우회 가능한지 검증 |
| `point/05_point_concurrent_charge.js` | 개발용 충전 API 기준 포인트 낙관락/재시도 충돌 검증 |
| `point/06_point_concurrent_use_via_payment.js` | 주문/결제 플로우를 통한 포인트 동시 사용 정합성 검증 |
| `point/07_point_refund_idempotency.js` | 동일 주문 취소 동시 요청 시 포인트 복구 멱등성 검증 |
| `point/08_point_expire_batch.js` | 포인트 만료 배치 수동 실행 및 재실행 멱등성 검증 |

## EC2 설치

```bash
cd k6-auth-security-point
./scripts/install-k6-ubuntu.sh
```

## 환경 변수 설정

```bash
cp .env.example .env
vi .env
source .env
```

필수값:

```bash
export BASE_URL="https://onestop1.duckdns.org"
export USER_EMAIL="loadtest@example.com"
export DEFAULT_PASSWORD="Test1234!"
export ITEM_ID="1" # stock 충분한 ProductItem ID
export ADMIN_EMAIL="superadmin@example.com" # 포인트 배치용
export ADMIN_PASSWORD="Test1234!"
```

## 실행

개별 실행:

```bash
./scripts/run-one.sh auth/01_refresh_race.js
./scripts/run-one.sh security/03_login_rate_limit.js
./scripts/run-one.sh point/06_point_concurrent_use_via_payment.js
```

전체 실행:

```bash
./scripts/run-all.sh
```

## 권장 실행 순서

1. `auth/01_refresh_race.js`
2. `auth/02_device_limit_race.js`
3. `security/03_login_rate_limit.js`
4. `security/04_ip_spoofing_ratelimit.js`
5. `point/05_point_concurrent_charge.js`
6. `point/06_point_concurrent_use_via_payment.js`
7. `point/07_point_refund_idempotency.js`
8. `point/08_point_expire_batch.js`

## 주의사항

### 1. Rate Limit 간섭

기기 제한 테스트, 주문/결제 포인트 테스트는 기존 Rate Limit 정책에 의해 429가 발생할 수 있습니다. 부하테스트용 `dev`/`test` 프로파일에서 정책치를 높이거나 테스트 계정을 분산하세요.

### 2. 포인트 충전 API

`POST /api/users/me/points/charge`는 `local`, `test`, `dev` 프로파일에서만 등록됩니다. 운영 프로파일에서 실행하면 404/403이 정상입니다. 운영에 가까운 배포 서버에서 포인트 테스트를 하려면 사전에 DB seed 또는 관리자 기능으로 포인트를 적립해 두세요.

### 3. 포인트 사용 테스트

`point/06_point_concurrent_use_via_payment.js`는 실제 포인트 사용 API가 아니라 아래 흐름을 사용합니다.

```text
POST /api/orders
POST /api/payments
```

즉, 실제 결제 승인 트랜잭션 안에서 `pointService.usePoint()`가 호출되는 구조를 검증합니다.

### 4. 포인트 복구 테스트

`point/07_point_refund_idempotency.js`는 하나의 주문을 결제 완료한 뒤 동일 주문 취소 요청을 동시에 보냅니다. 기대값은 `cancel_success_count == 1`입니다. 나머지 요청은 400/409 등 비즈니스 실패로 처리되어도 됩니다. 중요한 것은 500이 없고 포인트가 중복 복구되지 않는 것입니다.

### 5. X-Forwarded-For 테스트

`security/04_ip_spoofing_ratelimit.js`는 일부러 매 요청마다 다른 `X-Forwarded-For`를 보냅니다. 이 테스트에서 `blocked_count`가 0이면 Nginx 또는 `ClientIpExtractor`가 클라이언트 조작 XFF를 신뢰하고 있을 가능성이 있습니다.

## 문서화할 핵심 지표

공통:

- 총 요청 수
- 성공 수
- 실패 수
- 평균 응답 시간
- P95 / P99
- Error Rate
- 500 Error Count
- CPU / Memory
- DB Connection Pool
- Redis Latency

Auth:

- Refresh 성공 수: 1
- Refresh 실패 수: N-1
- 기존 RT 재사용 실패 여부
- 최종 Redis RT 1개 여부
- 최종 device ZSET 개수 <= 5

Security:

- Rate Limit 차단 수
- 허용 요청 수
- Redis TTL
- XFF 조작 우회 여부

Point:

- 최종 잔액
- 잔액 음수 발생 여부
- 성공한 결제 수
- PointHistory USE/REFUND 개수
- PointUsageDetail 합계
- 중복 복구 여부
- 만료 배치 누락/중복 여부
