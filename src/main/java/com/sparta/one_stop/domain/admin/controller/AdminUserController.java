package com.sparta.one_stop.domain.admin.controller;

import com.sparta.one_stop.domain.admin.dto.AdminUserResponse;
import com.sparta.one_stop.domain.admin.service.AdminUserService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - User Management", description = "관리자 권한 관리 API (SUPER_ADMIN 전용)")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "관리자 목록 조회 (ADMIN/SUPER_ADMIN 계정만)")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getAdminUsers(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getAdminUsers(pageable)));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "관리자 권한 부여 (BUYER → ADMIN)")
    @PatchMapping("/{userId}/grant")
    public ResponseEntity<ApiResponse<Void>> grantAdmin(
        @PathVariable Long userId,
        @AuthenticationPrincipal AuthUser authUser
    ) {
        adminUserService.grantAdmin(userId, authUser.userId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "관리자 권한 회수 (ADMIN → BUYER)")
    @PatchMapping("/{userId}/revoke")
    public ResponseEntity<ApiResponse<Void>> revokeAdmin(
        @PathVariable Long userId,
        @AuthenticationPrincipal AuthUser authUser
    ) {
        adminUserService.revokeAdmin(userId, authUser.userId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
