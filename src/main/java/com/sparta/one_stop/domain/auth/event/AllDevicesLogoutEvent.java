package com.sparta.one_stop.domain.auth.event;

/**
 * 사용자의 모든 기기 강제 로그아웃 이벤트
 *
 * 발행 시점:
 *   - 비밀번호 변경 후 (PASSWORD_CHANGED)
 *   - 회원 탈퇴 후 (WITHDRAWN)
 *   - 관리자 정지 후 (SUSPENDED)
 *   - 보안 침해 의심 시 (SECURITY_BREACH)
 *
 * 처리 방식:
 *   - DB 트랜잭션 커밋 후 Listener가 처리 (AFTER_COMMIT)
 *   - Redis 정리 + 알림 발송 + 감사 로그
 *
 * 트랜잭션 일관성:
 *   - DB 커밋 실패 시 이벤트도 발행 안 됨
 *   - DB만 변경되고 Redis 안 정리되는 상황 방지
 */
public record AllDevicesLogoutEvent(
    Long userId,
    String reason  // PASSWORD_CHANGED / WITHDRAWN / SUSPENDED / SECURITY_BREACH
) {
}

