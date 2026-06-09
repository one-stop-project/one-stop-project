package com.sparta.one_stop.global.audit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SecurityAuditEventType {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  인증 (Authentication)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    LOGIN_SUCCESS               (Category.AUTH, Severity.INFO,     "로그인 성공"),
    LOGIN_FAILED_BAD_CREDENTIALS(Category.AUTH, Severity.MEDIUM,   "로그인 실패 — 비밀번호 불일치"),
    LOGIN_FAILED_ACCOUNT_LOCKED (Category.AUTH, Severity.HIGH,     "로그인 실패 — 계정 잠금"),
    LOGIN_FAILED_USER_NOT_FOUND (Category.AUTH, Severity.MEDIUM,   "로그인 실패 — 존재하지 않는 사용자"),
    LOGIN_RATE_LIMIT_EXCEEDED   (Category.AUTH, Severity.HIGH,     "로그인 Rate Limit 초과 (무차별 대입 의심)"),
    LOGOUT                      (Category.AUTH, Severity.INFO,     "로그아웃"),

    // 토큰 관련
    TOKEN_REFRESH_SUCCESS       (Category.AUTH, Severity.INFO,     "토큰 재발급 성공"),
    TOKEN_REFRESH_FAILED        (Category.AUTH, Severity.MEDIUM,   "토큰 재발급 실패"),
    TOKEN_DEVICE_MISMATCH       (Category.AUTH, Severity.CRITICAL, "토큰 deviceId 불일치 — 탈취 의심!"),
    TOKEN_EXPIRED               (Category.AUTH, Severity.INFO,     "토큰 만료"),
    TOKEN_INVALID_SIGNATURE     (Category.AUTH, Severity.CRITICAL, "토큰 서명 위조 시도"),
    TOKEN_BLACKLISTED           (Category.AUTH, Severity.HIGH,     "블랙리스트 토큰 사용 시도"),

    // 회원가입/탈퇴
    SIGNUP_SUCCESS              (Category.AUTH, Severity.INFO,     "회원가입 성공"),
    SIGNUP_FAILED_DUPLICATE     (Category.AUTH, Severity.MEDIUM,   "회원가입 실패 — 이메일 중복"),
    PASSWORD_CHANGED            (Category.AUTH, Severity.MEDIUM,   "비밀번호 변경 — 전체 기기 로그아웃"),
    ACCOUNT_WITHDRAWN           (Category.AUTH, Severity.MEDIUM,   "회원 탈퇴"),

    // 다중 기기
    DEVICE_LIMIT_EXCEEDED       (Category.AUTH, Severity.MEDIUM,   "기기 한도 초과 — 최古 기기 자동 추방"),
    OAUTH2_LOGIN_SUCCESS        (Category.AUTH, Severity.INFO,     "OAuth2 로그인 성공"),
    OAUTH2_LOGIN_FAILED         (Category.AUTH, Severity.MEDIUM,   "OAuth2 로그인 실패"),

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  권한 (Authorization)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    ACCESS_DENIED               (Category.AUTHZ, Severity.MEDIUM,  "접근 권한 없음"),
    PRIVILEGE_ESCALATION_ATTEMPT(Category.AUTHZ, Severity.CRITICAL,"권한 상승 시도 — 보안 침해 의심!"),
    IDOR_ATTEMPT                (Category.AUTHZ, Severity.HIGH,    "타인 리소스 접근 시도 (IDOR)"),
    ADMIN_API_ACCESS            (Category.AUTHZ, Severity.MEDIUM,  "관리자 API 호출"),
    SUPER_ADMIN_API_ACCESS      (Category.AUTHZ, Severity.HIGH,    "최상위 관리자 API 호출 — 감사 추적 필수"),

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Seller 도메인
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    SELLER_PRODUCT_CREATE       (Category.SELLER, Severity.INFO,   "판매자 상품 등록"),
    SELLER_PRODUCT_UPDATE       (Category.SELLER, Severity.INFO,   "판매자 상품 수정"),
    SELLER_PRODUCT_DELETE       (Category.SELLER, Severity.MEDIUM, "판매자 상품 삭제"),
    SELLER_FOREIGN_PRODUCT_ACCESS(Category.SELLER, Severity.HIGH,  "다른 판매자 상품 접근 시도 — IDOR"),
    SELLER_INVENTORY_INBOUND    (Category.SELLER, Severity.MEDIUM, "판매자 재고 입고"),
    SELLER_INVENTORY_ADJUSTMENT (Category.SELLER, Severity.MEDIUM, "판매자 재고 보정 (직접 수정)"),
    SELLER_INVENTORY_MAX_EXCEEDED(Category.SELLER, Severity.HIGH,  "재고 한도 초과 시도 — 비정상"),
    SELLER_STATUS_CHANGE        (Category.SELLER, Severity.MEDIUM, "판매자 상태 변경 (승인/반려/정지/재활성)"),

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  포인트 도메인 (돈 관련 — 가장 민감)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    POINT_CHARGE                (Category.POINT, Severity.MEDIUM,  "포인트 충전"),
    POINT_USE                   (Category.POINT, Severity.MEDIUM,  "포인트 사용"),
    POINT_REFUND                (Category.POINT, Severity.MEDIUM,  "포인트 환불/복구"),
    POINT_EARN                  (Category.POINT, Severity.INFO,    "포인트 적립"),
    POINT_EXPIRE                (Category.POINT, Severity.INFO,    "포인트 만료"),
    POINT_INTEGRITY_VIOLATION   (Category.POINT, Severity.CRITICAL,"포인트 무결성 위반 — DB 직접 조작 의심!"),
    POINT_INSUFFICIENT          (Category.POINT, Severity.INFO,    "포인트 잔액 부족"),
    POINT_ABNORMAL_USAGE        (Category.POINT, Severity.HIGH,    "비정상 포인트 사용 패턴 감지"),

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Admin (관리자 작업)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    ADMIN_USER_SUSPEND          (Category.ADMIN, Severity.HIGH,    "관리자: 회원 정지"),
    ADMIN_USER_REACTIVATE       (Category.ADMIN, Severity.MEDIUM,  "관리자: 회원 재활성화"),
    ADMIN_SELLER_APPROVE        (Category.ADMIN, Severity.MEDIUM,  "관리자: 판매자 승인"),
    ADMIN_SELLER_REJECT         (Category.ADMIN, Severity.MEDIUM,  "관리자: 판매자 반려"),
    ADMIN_DATA_EXPORT           (Category.ADMIN, Severity.HIGH,    "관리자: 대량 데이터 내보내기"),
    ADMIN_CONFIG_CHANGE         (Category.ADMIN, Severity.HIGH,    "관리자: 시스템 설정 변경"),

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  의심 행위 / 일반
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    SUSPICIOUS_PATTERN_DETECTED (Category.OTHER, Severity.HIGH,    "이상 패턴 자동 탐지"),
    UNKNOWN_ERROR               (Category.OTHER, Severity.MEDIUM,  "분류 불가 — 수동 검토 필요");

    private final Category category;
    private final Severity severity;
    private final String description;

    /** 이벤트 카테고리 — Admin 대시보드에서 필터링 사용 */
    public enum Category {
        AUTH, AUTHZ, SELLER, POINT, ADMIN, OTHER
    }

    /** 위협도 — 알림 정책 결정 */
    @Getter
    @RequiredArgsConstructor
    public enum Severity {
        CRITICAL(4, "즉시 알림"),
        HIGH    (3, "일일 리포트 + 패턴 감지"),
        MEDIUM  (2, "주간 통계"),
        INFO    (1, "보관용");

        private final int level;
        private final String policy;

        public boolean isAtLeast(Severity other) {
            return this.level >= other.level;
        }
    }
}
