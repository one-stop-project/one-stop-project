package com.sparta.one_stop.domain.seller.controller;

import com.sparta.one_stop.domain.seller.dto.response.SellerReviewResponse;
import com.sparta.one_stop.domain.seller.dto.response.SellerReviewSummaryResponse;
import com.sparta.one_stop.domain.seller.service.SellerReviewService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.response.PageResponse;
import com.sparta.one_stop.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller")
public class SellerReviewController {

    private final SellerReviewService sellerReviewService;

    @Operation(summary = "판매자 전체 상품 리뷰 조회")
    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<PageResponse<SellerReviewResponse>>> getReviews(
        @AuthenticationPrincipal AuthUser authUser,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(
            sellerReviewService.getReviews(authUser.userId(), pageable))));
    }

    @Operation(summary = "판매자 상품별 리뷰 조회")
    @GetMapping("/products/{productId}/reviews")
    public ResponseEntity<ApiResponse<PageResponse<SellerReviewResponse>>> getProductReviews(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long productId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(
            sellerReviewService.getProductReviews(authUser.userId(), productId, pageable))));
    }

    @Operation(summary = "판매자 리뷰 요약 조회")
    @GetMapping("/reviews/summary")
    public ResponseEntity<ApiResponse<SellerReviewSummaryResponse>> getReviewSummary(
        @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            sellerReviewService.getReviewSummary(authUser.userId())));
    }
}
