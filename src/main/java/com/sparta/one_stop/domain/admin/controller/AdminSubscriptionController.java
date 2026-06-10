package com.sparta.one_stop.domain.admin.controller;

import com.sparta.one_stop.domain.admin.dto.AdminSubscriptionResponse;
import com.sparta.one_stop.domain.admin.dto.AdminSubscriptionStatsResponse;
import com.sparta.one_stop.domain.admin.service.AdminSubscriptionService;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Subscription", description = "관리자 구독 현황 API")
@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
public class AdminSubscriptionController {

    private final AdminSubscriptionService adminSubscriptionService;

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "구독 상태별 통계 조회")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminSubscriptionStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(adminSubscriptionService.getStats()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "전체 구독 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminSubscriptionResponse>>> getSubscriptions(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminSubscriptionService.getSubscriptions(pageable)));
    }
}
