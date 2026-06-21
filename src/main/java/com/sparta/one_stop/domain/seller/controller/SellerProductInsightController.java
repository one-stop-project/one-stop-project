package com.sparta.one_stop.domain.seller.controller;

import com.sparta.one_stop.domain.seller.dto.response.SellerProductRejectReasonResponse;
import com.sparta.one_stop.domain.seller.service.SellerProductInsightService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller/products")
public class SellerProductInsightController {

    private final SellerProductInsightService sellerProductInsightService;

    @Operation(summary = "상품 반려 사유 조회")
    @GetMapping("/{productId}/reject-reason")
    public ResponseEntity<ApiResponse<SellerProductRejectReasonResponse>> getRejectReason(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long productId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            sellerProductInsightService.getRejectReason(authUser.userId(), productId)));
    }
}
