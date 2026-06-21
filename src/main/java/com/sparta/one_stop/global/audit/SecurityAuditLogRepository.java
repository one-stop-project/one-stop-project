package com.sparta.one_stop.global.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, Long> {

    Page<SecurityAuditLog> findByActorUserIdOrderByOccurredAtDesc(Long userId, Pageable pageable);

    Page<SecurityAuditLog> findByEventTypeOrderByOccurredAtDesc(
        SecurityAuditEventType type,
        Pageable pageable
    );

    Page<SecurityAuditLog> findByEventTypeAndSuspiciousOrderByOccurredAtDesc(
        SecurityAuditEventType type,
        boolean suspicious,
        Pageable pageable
    );

    Page<SecurityAuditLog> findByEventTypeAndSuspiciousAndOccurredAtBetweenOrderByOccurredAtDesc(
        SecurityAuditEventType type,
        boolean suspicious,
        LocalDateTime from,
        LocalDateTime to,
        Pageable pageable
    );

    long countByClientIpHashAndEventTypeAndOccurredAtAfter(
        String hash,
        SecurityAuditEventType type,
        LocalDateTime since
    );

    long countByActorUserIdAndEventTypeAndOccurredAtAfter(
        Long userId,
        SecurityAuditEventType type,
        LocalDateTime since
    );

    Page<SecurityAuditLog> findBySeverityAndOccurredAtAfterOrderByOccurredAtDesc(
        SecuritySeverity severity,
        LocalDateTime since,
        Pageable pageable
    );

    Page<SecurityAuditLog> findBySeverityInAndOccurredAtAfterOrderByOccurredAtDesc(
        List<SecuritySeverity> severities,
        LocalDateTime since,
        Pageable pageable
    );

    @Query("SELECT l.category, COUNT(l) FROM SecurityAuditLog l "
        + "WHERE l.occurredAt > :since GROUP BY l.category")
    List<Object[]> countGroupedByCategory(@Param("since") LocalDateTime since);
}
