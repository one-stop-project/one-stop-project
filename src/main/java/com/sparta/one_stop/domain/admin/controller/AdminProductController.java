package com.sparta.one_stop.domain.admin.controller;

import com.sparta.one_stop.domain.admin.dto.ProductResponse;
import com.sparta.one_stop.domain.admin.service.AdminProductService;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin - Product", description = "상품 승인/반려 관리 API")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService adminProductService;

    // 승인 요청된 상품 목록 조회
    @Operation(summary = "승인 요청된 상품 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getPendingProducts() {
        List<ProductResponse> response = adminProductService.getPendingProducts()
            .stream()
            .map(ProductResponse::from)
            .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 상품 승인
    @Operation(summary = "상품 승인")
    @PostMapping("/{productId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveProduct(@PathVariable Long productId) {
        adminProductService.approveProduct(productId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 상품 반려
    @Operation(summary = "상품 반려")
    @PostMapping("/{productId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectProduct(@PathVariable Long productId) {
        adminProductService.rejectProduct(productId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // 상품 강제 비활성화
    @Operation(summary = "상품 강제 비활성화")
    @PostMapping("/{productId}/force-inactive")
    public ResponseEntity<ApiResponse<Void>> forceInactiveProduct(@PathVariable Long productId) {
        adminProductService.forceInactiveProduct(productId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
