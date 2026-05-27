package com.sparta.one_stop.domain.admin.controller;

import com.sparta.one_stop.domain.admin.dto.AdminRejectRequest;
import com.sparta.one_stop.domain.admin.dto.ProductResponse;
import com.sparta.one_stop.domain.admin.service.AdminProductService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Product", description = "상품 승인/반려 관리 API")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    // TODO: M3 완료 후 @PreAuthorize("hasRole('ADMIN')") 또는
    //       @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')") 추가 예정
    // 현재는 SecurityConfig URL 패턴으로 ADMIN 권한 제어 중

    private final AdminProductService adminProductService;

    // 승인 요청된 상품 목록 조회 (페이징)
    @Operation(summary = "승인 요청된 상품 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getPendingProducts(
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ProductResponse> response = adminProductService.getPendingProducts(pageable)
            .map(ProductResponse::from);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 상품 승인 (PATCH로 변경)
    @Operation(summary = "상품 승인")
    @PatchMapping("/{productId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveProduct(
        @PathVariable Long productId,
        @AuthenticationPrincipal AuthUser authUser
    ) {
        adminProductService.approveProduct(productId, authUser.userId());
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 상품 반려 (PATCH로 변경 + reason 필수)
    @Operation(summary = "상품 반려")
    @PatchMapping("/{productId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectProduct(
        @PathVariable Long productId,
        @Valid @RequestBody AdminRejectRequest request,
        @AuthenticationPrincipal AuthUser authUser
    ) {
        adminProductService.rejectProduct(productId, authUser.userId(), request.reason());
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 상품 강제 비활성화 (PATCH로 변경 + reason 필수)
    @Operation(summary = "상품 강제 비활성화")
    @PatchMapping("/{productId}/force-inactive")
    public ResponseEntity<ApiResponse<Void>> forceInactiveProduct(
        @PathVariable Long productId,
        @Valid @RequestBody AdminRejectRequest request,
        @AuthenticationPrincipal AuthUser authUser
    ) {
        adminProductService.forceInactiveProduct(productId, authUser.userId(), request.reason());
        return ResponseEntity.ok(ApiResponse.success());
    }
}
