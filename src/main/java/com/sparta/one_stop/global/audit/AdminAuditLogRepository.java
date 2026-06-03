package com.sparta.one_stop.global.audit;

import com.sparta.one_stop.global.audit.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자/시스템 작업 감사 로그 저장소
 *
 * <p><b>⚠️ 마이그레이션 노트 (10년차 개발자 관점)</b>
 * <p>이 Repository는 {@link AdminAuditLog} 전용으로, 주로 <b>도메인 작업 감사</b>(예: 포인트 사용,
 * 환불, 회원 정지 등)에 사용됩니다.
 *
 * <p>장기적으로는 {@code SecurityAuditLogRepository}로 통합 권장:
 * <ul>
 *   <li>두 시스템 병존 시 — "이거 어디 기록해야 하지?" 결정 비용 발생</li>
 *   <li>사고 분석 시 두 테이블 JOIN 필요</li>
 *   <li>동일 이벤트 중복 기록 가능성</li>
 * </ul>
 *
 * <p><b>현재 운영 가이드</b> — 둘 중 어디에 기록할지 명확히
 * <table border="1">
 *   <tr><th>이벤트 종류</th><th>저장소</th></tr>
 *   <tr><td>포인트 도메인 작업 (PointService 메서드)</td><td>AdminAuditLog (현재)</td></tr>
 *   <tr><td>인증/권한 실패, IDOR, 보안 위협</td><td>SecurityAuditLog (신규)</td></tr>
 *   <tr><td>새로 추가되는 도메인 감사</td><td>SecurityAuditLog (신규)</td></tr>
 * </table>
 *
 * <p><b>⚠️ 중요</b>: 이 Repository는 <b>읽기/생성만</b> 사용. {@code update/delete}는 금지
 * (DB 트리거로 강제됨).
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  단순 조회 — 메서드 쿼리
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** 액션 코드로 필터 (POINT_USE, POINT_REFUND 등) — 최신순 */
    Page<AdminAuditLog> findByActionOrderByOccurredAtDesc(String action, Pageable pageable);

    /** 대상 리소스로 필터 (Point, User 등) — 최신순 */
    Page<AdminAuditLog> findByTargetResourceOrderByOccurredAtDesc(String targetResource, Pageable pageable);

    /** 결과(SUCCESS/FAILURE)로 필터 — 실패 이벤트 분석용 */
    Page<AdminAuditLog> findByResultOrderByOccurredAtDesc(String result, Pageable pageable);

    /** 특정 IP에서 발생한 이벤트 — 보안 사고 분석용 */
    Page<AdminAuditLog> findByClientIpOrderByOccurredAtDesc(String clientIp, Pageable pageable);

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  복합 조회 — @Query
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 특정 기간 + 액션 필터 — 운영 리포트용
     *
     * <p>예: "지난주 POINT_USE 이벤트 전체"
     */
    @Query("""
            SELECT aal FROM AdminAuditLog aal
             WHERE aal.action = :action
               AND aal.occurredAt BETWEEN :from AND :to
             ORDER BY aal.occurredAt DESC
            """)
    Page<AdminAuditLog> findByActionBetween(
        @Param("action") String action,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable);

    /**
     * 짧은 시간 내 다발 실패 — 비정상 패턴 감지용
     *
     * <p>예: "최근 5분간 POINT_USE 실패가 10회 이상이면 의심"
     *
     * @param action 액션 코드
     * @param since  기준 시간 (예: NOW - 5분)
     * @return 실패 횟수
     */
    @Query("""
            SELECT COUNT(aal) FROM AdminAuditLog aal
             WHERE aal.action = :action
               AND aal.result = 'FAILURE'
               AND aal.occurredAt >= :since
            """)
    long countRecentFailures(@Param("action") String action, @Param("since") LocalDateTime since);

    /**
     * 특정 IP의 최근 실패 카운트 — Rate limiting 보조 / 이상 탐지용
     */
    @Query("""
            SELECT COUNT(aal) FROM AdminAuditLog aal
             WHERE aal.clientIp = :ip
               AND aal.result = 'FAILURE'
               AND aal.occurredAt >= :since
            """)
    long countRecentFailuresByIp(@Param("ip") String ip, @Param("since") LocalDateTime since);

    /**
     * 액션 + 결과 + 기간 다중 조건 — Admin 대시보드용
     */
    @Query("""
            SELECT aal FROM AdminAuditLog aal
             WHERE (:action IS NULL OR aal.action = :action)
               AND (:result IS NULL OR aal.result = :result)
               AND aal.occurredAt BETWEEN :from AND :to
             ORDER BY aal.occurredAt DESC
            """)
    Page<AdminAuditLog> search(
        @Param("action") String action,
        @Param("result") String result,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable);

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  통계 — 대시보드용
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 액션별 카운트 (지난 N일) — 대시보드 차트용
     *
     * <p>반환: Object[] = { action(String), count(Long) }
     */
    @Query("""
            SELECT aal.action, COUNT(aal)
              FROM AdminAuditLog aal
             WHERE aal.occurredAt BETWEEN :from AND :to
             GROUP BY aal.action
             ORDER BY COUNT(aal) DESC
            """)
    List<Object[]> countByActionBetween(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    /**
     * 성공/실패 비율 (지난 N일) — 운영 헬스 체크
     *
     * <p>반환: Object[] = { result(String), count(Long) }
     */
    @Query("""
            SELECT aal.result, COUNT(aal)
              FROM AdminAuditLog aal
             WHERE aal.occurredAt BETWEEN :from AND :to
             GROUP BY aal.result
            """)
    List<Object[]> countByResultBetween(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);
}
