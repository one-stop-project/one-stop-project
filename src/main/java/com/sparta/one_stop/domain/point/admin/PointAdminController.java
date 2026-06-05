package com.sparta.one_stop.domain.point.admin;

import com.sparta.one_stop.domain.point.scheduler.PointExpireScheduler;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Admin 포인트 운영/감사 API
 *
 * <p><b>권한</b>: SUPER_ADMIN 만 호출 가능 (ADMIN도 차단 — 가장 민감한 영역)
 */
@RestController
@RequestMapping("/api/admin/points")
@RequiredArgsConstructor
@Validated
public class PointAdminController {

    private final PointAdminService adminService;
    private final PointExpireScheduler batchScheduler;

    /**
     * 시스템 전체 포인트 통계 + 회계 항등식 검증
     *
     * <p>대시보드 / 일일 마감 / 회계 보고 시 사용
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PointSystemStats> getStats() {
        return ResponseEntity.ok(adminService.getSystemStats());
    }

    /**
     * 정합성 불일치 사용자 검출
     *
     * <p>{@code balance != SUM(remainingAmount)} 인 사용자 리스트
     */
    @GetMapping("/inconsistencies")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<PointInconsistencyReport>> findInconsistent(
        @RequestParam(defaultValue = "100")
        @Min(value = 1, message = "limit은 1 이상이어야 합니다")
        @Max(value = 1000, message = "limit은 1000 이하여야 합니다")
        int limit){
        return ResponseEntity.ok(adminService.findInconsistentUsers(limit));
    }

    /**
     * 만료 배치 수동 실행
     *
     * <p>스케줄 외 시점에 즉시 실행 필요할 때 (장애 복구, 테스트 등)
     */
    @PostMapping("/expire/run")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> runExpirationManually(
        @RequestParam(required = false) LocalDate targetDate) {

        LocalDate date = targetDate != null ? targetDate : LocalDate.now();
        String result = batchScheduler.runManually(date);

        return ResponseEntity.ok(Map.of(
            "targetDate", date.toString(),
            "result", result
        ));
    }
}


