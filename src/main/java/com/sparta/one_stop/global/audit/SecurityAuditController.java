package com.sparta.one_stop.global.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/security-audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class SecurityAuditController {

    private final SecurityAuditLogRepository repository;

    /**
     * 특정 사용자의 보안 이벤트 조회
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<Page<SecurityAuditLog>> findByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        var pageable = PageRequest.of(page, size, Sort.by("occurredAt").descending());
        return ResponseEntity.ok(repository.findByActorUserIdOrderByOccurredAtDesc(userId, pageable));
    }

    /**
     * 이벤트 유형으로 필터 조회
     */
    @GetMapping("/events")
    public ResponseEntity<Page<SecurityAuditLog>> findByEventType(
            @RequestParam SecurityAuditEventType eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        var pageable = PageRequest.of(page, size, Sort.by("occurredAt").descending());
        return ResponseEntity.ok(repository.findByEventTypeOrderByOccurredAtDesc(eventType, pageable));
    }

    /**
     * 고위험 이벤트 — CRITICAL/HIGH만
     * 최근 7일 기본
     */
    @GetMapping("/high-risk")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<SecurityAuditLog>> findHighRisk(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        LocalDateTime from = LocalDateTime.now().minusDays(days);
        var pageable = PageRequest.of(page, size);

        return ResponseEntity.ok(repository.findHighRiskEvents(
                List.of(SecurityAuditEventType.Severity.CRITICAL,
                        SecurityAuditEventType.Severity.HIGH),
                from, pageable));
    }

    /**
     * CRITICAL 이벤트만 조회 — 즉시 대응 필요한 항목
     */
    @GetMapping("/critical")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<SecurityAuditLog>> findCritical(
            @RequestParam(defaultValue = "24") int hours) {

        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return ResponseEntity.ok(repository.findCriticalSince(since));
    }

    /**
     * 카테고리별 통계 — 대시보드용
     */
    @GetMapping("/stats/by-category")
    public ResponseEntity<Map<String, Long>> statsByCategory(
            @RequestParam(defaultValue = "7") int days) {

        LocalDateTime from = LocalDateTime.now().minusDays(days);
        LocalDateTime to = LocalDateTime.now();

        Map<String, Long> stats = repository.countByCategoryBetween(from, to).stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1]));

        return ResponseEntity.ok(stats);
    }
}
