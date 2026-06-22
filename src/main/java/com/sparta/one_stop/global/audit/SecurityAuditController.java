package com.sparta.one_stop.global.audit;

import com.sparta.one_stop.global.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class SecurityAuditController {

    private final SecurityAuditLogRepository repository;
    private final SecurityAuditService audit;

    @GetMapping("/api/admin/security/audit-logs")
    public ResponseEntity<ApiResponse<Page<SecurityAuditLogResponse>>> search(
        @RequestParam SecurityAuditEventType eventType,
        @RequestParam(defaultValue = "false") boolean suspicious,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Page<SecurityAuditLogResponse> result = repository
            .findByEventTypeAndSuspiciousOrderByOccurredAtDesc(
                eventType,
                suspicious,
                PageRequest.of(page, size)
            )
            .map(SecurityAuditLogResponse::from);
        audit.record(SecurityAuditEvent.builder()
            .eventType(SecurityAuditEventType.SECURITY_AUDIT_LOG_VIEWED)
            .result("SUCCESS")
            .ruleCode("AUDIT_VIEW")
            .build());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/api/admin/security-audit/critical")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<AdminAuditView>> critical(
        @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return ResponseEntity.ok(repository
            .findBySeverityAndOccurredAtAfterOrderByOccurredAtDesc(
                SecuritySeverity.CRITICAL,
                since,
                PageRequest.of(page, size)
            )
            .map(AdminAuditView::from));
    }

    @GetMapping("/api/admin/security-audit/high-risk")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<AdminAuditView>> highRisk(
        @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return ResponseEntity.ok(repository
            .findBySeverityInAndOccurredAtAfterOrderByOccurredAtDesc(
                List.of(SecuritySeverity.CRITICAL, SecuritySeverity.HIGH),
                since,
                PageRequest.of(page, size)
            )
            .map(AdminAuditView::from));
    }

    @GetMapping("/api/admin/security-audit/stats/by-category")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, Long>> statsByCategory(
        @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days
    ) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = repository.countGroupedByCategory(since);
        Map<String, Long> stats = new LinkedHashMap<>();
        for (Object[] row : rows) {
            stats.put(row[0].toString(), (Long) row[1]);
        }
        return ResponseEntity.ok(stats);
    }

    record AdminAuditView(
        Long id,
        String eventType,
        SecuritySeverity severity,
        String category,
        Long actorUserId,
        String actorEmail,
        String actorRole,
        String targetResource,
        String targetId,
        String result,
        String errorCode,
        String clientIp,
        String requestUri,
        LocalDateTime occurredAt
    ) {
        static AdminAuditView from(SecurityAuditLog log) {
            return new AdminAuditView(
                log.getId(),
                log.getEventType().name(),
                log.getSeverity(),
                log.getCategory() != null ? log.getCategory().name() : null,
                log.getActorUserId(),
                null,
                log.getActorRole(),
                log.getTargetResource(),
                log.getTargetId(),
                log.getResult(),
                log.getErrorCode(),
                log.getClientIpPrefix(),
                log.getRequestPath(),
                log.getOccurredAt()
            );
        }
    }
}
