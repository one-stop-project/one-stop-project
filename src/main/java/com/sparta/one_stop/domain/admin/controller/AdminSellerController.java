package com.sparta.one_stop.domain.admin.controller;

import com.sparta.one_stop.domain.admin.dto.AdminRejectRequest;
import com.sparta.one_stop.domain.admin.dto.SellerResponse;
import com.sparta.one_stop.domain.admin.service.AdminSellerService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin - Seller", description = "판매자 승인/반려 관리 API")
@RestController
@RequestMapping("/api/admin/sellers")
@RequiredArgsConstructor
public class AdminSellerController {

    private final AdminSellerService adminSellerService;

    // 대기 중인 판매자 목록 조회
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "대기 중인 판매자 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SellerResponse>>> getPendingSellers() {
        List<SellerResponse> response = adminSellerService.getPendingSellers()
            .stream()
            .map(SellerResponse::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 판매자 승인 (PATCH로 변경)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "판매자 승인")
    @PatchMapping("/{sellerId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveSeller(
        @PathVariable Long sellerId,
        @AuthenticationPrincipal AuthUser authUser
    ) {
        adminSellerService.approveSeller(sellerId, authUser.userId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 판매자 반려 (PATCH로 변경 + reason 필수)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "판매자 반려")
    @PatchMapping("/{sellerId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectSeller(
        @PathVariable Long sellerId,
        @Valid @RequestBody AdminRejectRequest request,
        @AuthenticationPrincipal AuthUser authUser
    ) {
        adminSellerService.rejectSeller(sellerId, authUser.userId(), request.reason());
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 판매자 강제 비활성화 (PATCH로 변경 + reason 필수)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "판매자 강제 비활성화")
    @PatchMapping("/{sellerId}/force-inactive")
    public ResponseEntity<ApiResponse<Void>> forceInactiveSeller(
        @PathVariable Long sellerId,
        @Valid @RequestBody AdminRejectRequest request,
        @AuthenticationPrincipal AuthUser authUser
    ) {
        adminSellerService.forceInactiveSeller(sellerId, authUser.userId(), request.reason());
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 판매자 정지 해제
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "판매자 정지 해제")
    @PatchMapping("/{sellerId}/reactivate")
    public ResponseEntity<ApiResponse<Void>> reactivateSeller(
        @PathVariable Long sellerId,
        @AuthenticationPrincipal AuthUser authUser
    ) {
        adminSellerService.reactivateSeller(sellerId, authUser.userId());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
