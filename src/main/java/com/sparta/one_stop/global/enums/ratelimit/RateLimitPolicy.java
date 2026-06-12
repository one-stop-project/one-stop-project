package com.sparta.one_stop.global.enums.ratelimit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * API 호출 제한 정책 (Rate Limit Policy)
 * ═══════════════════════════════════════════════════════════
 *  설계 원칙
 * ═══════════════════════════════════════════════════════════
 * 3계층 방어:
 *   1. 계정별  (가장 엄격) — 무차별 대입 공격 차단
 *   2. IP별   (중간)      — 한 IP에서의 다중 계정 공격 차단
 *   3. 글로벌 (가장 느슨) — 전체 서비스 보호
 * 시간 윈도우:
 *   - 짧을수록 정밀 (UX 영향 ↑)
 *   - 길수록 관대 (보안 효과 ↓)
 * ═══════════════════════════════════════════════════════════
 *  v1 대비 개선
 * ═══════════════════════════════════════════════════════════
 * 1. buildKey() 메서드 추가
 *    - 키 생성 로직을 Policy에 캡슐화
 *    - RateLimitService는 Policy.buildKey()만 호출
 *    - 향후 키 형식 변경 시 한 곳만 수정
 * 2. description 추가
 *    - 정책 의도를 코드에 명시
 *    - 로깅/디버깅 시 활용
 * 3. 정책 분류 강화
 *    - PASSWORD_RESET 추가 (이메일 폭탄 방어)
 *    - LOGIN_PER_GLOBAL 추가 (전체 서비스 보호)
 */
@Getter
@RequiredArgsConstructor
public enum RateLimitPolicy {

    // ──────────────────────────────────────────────
    //  회원가입
    // ──────────────────────────────────────────────
    SIGNUP_PER_IP(
        "signup:ip",
        3, 10 * 60,
        "회원가입 동일 IP 10분 3회 (어뷰징 가입 방어)"
    ),

    // ──────────────────────────────────────────────
    //  로그인 — 6계층 방어
    // ──────────────────────────────────────────────
    LOGIN_PER_ACCOUNT(
        "login:account",
        5, 60,
        "로그인 동일 계정 1분 5회 (무차별 대입 방어)"
    ),

    // 계정당 동시 로그인 성공 횟수 제한 — 인증은 통과해도 빠른 다발 차단
    LOGIN_CONCURRENT_PER_ACCOUNT(
        "login:concurrent",
        10,
        60,
        "계정당 동시 로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."
    ),

    // 짧은 시간 내 새 기기 등록 차단 — LRU 추방 폭주 방지
    DEVICE_REGISTER_PER_ACCOUNT(
        "login:deviceRegister",
        3,
        5 * 60,
        "짧은 시간에 너무 많은 새 기기에서 로그인되었습니다. 보안을 위해 잠시 후 시도해주세요."
    ),

    // 한 IP에서 여러 계정의 새 기기 등록 차단 — 분산 봇 공격 방어
    DEVICE_REGISTER_PER_IP(
        "login:device_per_ip",
        10,
        10 * 60,
        "비정상 접근이 감지되었습니다. 잠시 후 다시 시도해주세요."
    ),

    LOGIN_PER_IP(
        "login:ip",
        20, 60,
        "로그인 동일 IP 1분 20회 (DDoS 1차 방어)"
    ),

    LOGIN_PER_GLOBAL(
        "login:global",
        1000, 60,
        "로그인 전체 1분 1000회 (서비스 보호 최후 방어선)"
    ),

    // ──────────────────────────────────────────────
    //  토큰 재발급
    // ──────────────────────────────────────────────
    REFRESH_PER_DEVICE(
        "refresh:device",
        30, 60,
        "토큰 재발급 동일 기기 1분 30회 (탈취 토큰 무한 갱신 방어)"
    ),

    // ──────────────────────────────────────────────
    //  로그아웃 — permitAll(인증 없이 접근)이라 IP 기반 남용 방어
    // ──────────────────────────────────────────────
    LOGOUT_PER_IP(
        "logout:ip",
        20, 60,
        "로그아웃 동일 IP 1분 20회 (permitAll 엔드포인트 무제한 호출 방어)"
    ),

    // ──────────────────────────────────────────────
    //  비밀번호 재설정 (향후 기능)
    // ──────────────────────────────────────────────
    PASSWORD_RESET_PER_ACCOUNT(
        "password-reset:account",
        3, 60 * 60,
        "비밀번호 재설정 동일 계정 1시간 3회 (이메일 폭탄 방어)"
    ),

    // ──────────────────────────────────────────────
    //  결제
    // ──────────────────────────────────────────────
    PAYMENT_APPROVE_PER_USER(
        "payment:approve",
        5, 60,
        "결제 승인 동일 사용자 1분 5회 (중복 결제 시도 방어)"
    ),

    // ──────────────────────────────────────────────
    //  주문
    // ──────────────────────────────────────────────
    ORDER_CREATE_PER_USER(
        "order:create",
        10, 60,
        "주문 생성 동일 사용자 1분 10회 (재고 선점 어뷰징 방어)"
    ),

    // ──────────────────────────────────────────────
    //  쿠폰
    // ──────────────────────────────────────────────
    COUPON_ISSUE_PER_USER(
        "coupon:issue",
        10, 60,
        "쿠폰 발급 동일 사용자 1분 10회 (봇 쿠폰 독점 방어)"
    );

    /** Redis 키 prefix (정책 식별자) */
    private final String keyPrefix;

    /** 허용 횟수 */
    private final int limit;

    /** 기준 시간 (초) */
    private final int windowSeconds;

    /** 정책 설명 (로깅/디버깅) */
    private final String description;

    /**
     * Redis 키 생성 — 정책에 캡슐화
     * 형식: "ratelimit:{keyPrefix}:{identifier}"
     * 예시:
     *   - "ratelimit:login:account:user@example.com"
     *   - "ratelimit:payment:approve:1"
     *
     * @param identifier 식별자 (이메일, IP, deviceId, userId 등)
     * @return Redis 키
     */
    public String buildKey(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException(
                "Rate Limit identifier cannot be null or blank for policy: " + this.name()
            );
        }
        return "ratelimit:" + keyPrefix + ":" + identifier;
    }
}
