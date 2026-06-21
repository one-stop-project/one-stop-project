package com.sparta.one_stop.global.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog,Long> {
    Page<SecurityAuditLog> findByActorUserIdOrderByOccurredAtDesc(Long userId, Pageable pageable);
    Page<SecurityAuditLog> findByEventTypeOrderByOccurredAtDesc(SecurityAuditEventType type, Pageable pageable);
    Page<SecurityAuditLog> findByEventTypeAndSuspiciousOrderByOccurredAtDesc(
        SecurityAuditEventType type, boolean suspicious, Pageable pageable);
    Page<SecurityAuditLog> findByEventTypeAndSuspiciousAndOccurredAtBetweenOrderByOccurredAtDesc(
        SecurityAuditEventType type, boolean suspicious, LocalDateTime from, LocalDateTime to, Pageable pageable);
    long countByClientIpHashAndEventTypeAndOccurredAtAfter(String hash, SecurityAuditEventType type, LocalDateTime since);
    long countByActorUserIdAndEventTypeAndOccurredAtAfter(Long userId, SecurityAuditEventType type, LocalDateTime since);
}
