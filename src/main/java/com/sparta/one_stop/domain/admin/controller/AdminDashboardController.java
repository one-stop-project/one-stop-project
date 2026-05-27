package com.sparta.one_stop.domain.admin.controller;

import com.sparta.one_stop.domain.admin.dto.DashboardResponse;
import com.sparta.one_stop.domain.admin.service.AdminDashboardService;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Dashboard", description = "관리자 대시보드 API")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    // TODO: M3 완료 후 @PreAuthorize("hasRole('ADMIN')") 또는
    //       @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')") 추가 예정
    // 현재는 SecurityConfig URL 패턴으로 ADMIN 권한 제어 중

    private final AdminDashboardService adminDashboardService;

    // 관리자 대시보드 조회
    @Operation(summary = "관리자 대시보드 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getDashboard()));
    }
}
