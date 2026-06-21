package com.sparta.one_stop.domain.seller.controller;

import com.sparta.one_stop.domain.seller.dto.response.SellerOrderStatusCountResponse;
import com.sparta.one_stop.domain.seller.dto.response.SellerProductSalesStatResponse;
import com.sparta.one_stop.domain.seller.service.SellerDashboardService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.response.PageResponse;
import com.sparta.one_stop.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller/dashboard")
public class SellerDashboardController {

    private final SellerDashboardService sellerDashboardService;

    @Operation(summary = "주문상품 상태별 개수 조회")
    @GetMapping("/order-counts")
    public ResponseEntity<ApiResponse<SellerOrderStatusCountResponse>> getOrderStatusCounts(
        @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            sellerDashboardService.getOrderStatusCounts(authUser.userId())));
    }

    @Operation(
        summary = "상품별 판매 통계 조회",
        description = "주문 생성일이 조회 기간에 포함되고 현재 배송완료 상태인 주문상품을 집계합니다. 금액은 할인 전 금액입니다."
    )
    @GetMapping("/product-sales")
    public ResponseEntity<ApiResponse<PageResponse<SellerProductSalesStatResponse>>> getProductSalesStats(
        @AuthenticationPrincipal AuthUser authUser,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @PageableDefault(size = 10) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.from(
            sellerDashboardService.getProductSalesStats(authUser.userId(), from, to, pageable))));
    }
}
