package com.sparta.one_stop.global.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 보안 감사 로그 저장소
 *
 * <p><b>중요</b>: 이 Repository는 <b>읽기/생성만</b> 사용해야 함.
 * <br>{@code save()}는 신규 생성에만 사용, {@code update/delete}는 금지.
 */
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  검색 — Admin 대시보드용
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 특정 사용자의 모든 보안 이벤트 (최신순) */
    Page<SecurityAuditLog> findByActorUserIdOrderByOccurredAtDesc(Long actorUserId, Pageable pageable);

    /** 이벤트 유형으로 필터 */
    Page<SecurityAuditLog> findByEventTypeOrderByOccurredAtDesc(
            SecurityAuditEventType eventType, Pageable pageable);

    /** 위협도 이상 이벤트만 — CRITICAL/HIGH 알림용 */
    @Query("""
            SELECT sal FROM SecurityAuditLog sal
             WHERE sal.severity IN :severities
               AND sal.occurredAt >= :from
             ORDER BY sal.occurredAt DESC
            """)
    Page<SecurityAuditLog> findHighRiskEvents(
            @Param("severities") List<SecurityAuditEventType.Severity> severities,
            @Param("from") LocalDateTime from,
            Pageable pageable);

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  의심 행위 탐지용 쿼리
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 짧은 시간 내 다발 로그인 실패 — 무차별 대입 탐지용
     *
     * @param ip 클라이언트 IP
     * @param since 기준 시간 (예: NOW - 5분)
     * @return 해당 IP의 로그인 실패 횟수
     */
    @Query("""
            SELECT COUNT(sal) FROM SecurityAuditLog sal
             WHERE sal.clientIp = :ip
               AND sal.eventType IN (
                   com.sparta.one_stop.global.audit.SecurityAuditEventType.LOGIN_FAILED_BAD_CREDENTIALS,
                   com.sparta.one_stop.global.audit.SecurityAuditEventType.LOGIN_FAILED_USER_NOT_FOUND
               )
               AND sal.occurredAt >= :since
            """)
    long countRecentLoginFailuresByIp(@Param("ip") String ip, @Param("since") LocalDateTime since);

    /**
     * 특정 사용자의 짧은 시간 내 권한 위반 시도 횟수 — 권한 우회 탐지용
     */
    @Query("""
            SELECT COUNT(sal) FROM SecurityAuditLog sal
             WHERE sal.actorUserId = :userId
               AND sal.eventType IN (
                   com.sparta.one_stop.global.audit.SecurityAuditEventType.ACCESS_DENIED,
                   com.sparta.one_stop.global.audit.SecurityAuditEventType.IDOR_ATTEMPT,
                   com.sparta.one_stop.global.audit.SecurityAuditEventType.PRIVILEGE_ESCALATION_ATTEMPT
               )
               AND sal.occurredAt >= :since
            """)
    long countRecentAuthzViolationsByUser(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * CRITICAL 이벤트 — 알림 대기열 확인
     */
    @Query("""
            SELECT sal FROM SecurityAuditLog sal
             WHERE sal.severity = com.sparta.one_stop.global.audit.SecurityAuditEventType.Severity.CRITICAL
               AND sal.occurredAt >= :since
             ORDER BY sal.occurredAt DESC
            """)
    List<SecurityAuditLog> findCriticalSince(@Param("since") LocalDateTime since);

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  통계 — 대시보드용
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 카테고리별 이벤트 수 */
    @Query("""
            SELECT sal.category, COUNT(sal)
              FROM SecurityAuditLog sal
             WHERE sal.occurredAt BETWEEN :from AND :to
             GROUP BY sal.category
            """)
    List<Object[]> countByCategoryBetween(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
