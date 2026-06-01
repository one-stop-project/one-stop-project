package com.sparta.one_stop.domain.admin.controller;

import com.sparta.one_stop.domain.admin.dto.AdminActionHistoryResponse;
import com.sparta.one_stop.domain.admin.service.AdminActionHistoryService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Action History", description = "관리자 처리 이력 조회 API")
@RestController
@RequestMapping("/api/admin/action-histories")
@RequiredArgsConstructor
public class AdminActionHistoryController {

    private final AdminActionHistoryService adminActionHistoryService;

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "처리 이력 조회 (SUPER_ADMIN: 전체 / ADMIN: 본인)")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminActionHistoryResponse>>> getHistories(
        @AuthenticationPrincipal AuthUser authUser,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            adminActionHistoryService.getHistories(authUser.userId(), authUser.role(), pageable)
        ));
    }
}
