package com.sparta.one_stop.domain.product.controller;

import com.sparta.one_stop.domain.product.dto.response.ProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductSummaryResponse;
import com.sparta.one_stop.domain.product.service.BuyerProductService;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Buyer - Product", description = "구매자 상품 조회/검색 API")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class BuyerProductController {

    private final BuyerProductService buyerProductService;

    // 검색/목록: GET /api/products?keyword=&categoryId=&page=&size=&sort=
    @Operation(summary = "상품 검색/목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> search(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long categoryId,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(buyerProductService.search(keyword, categoryId, pageable)));
    }

    // 인기 상품: GET /api/products/popular — /{productId} 보다 먼저 선언해야 우선 매핑됨
    @Operation(summary = "인기 상품 조회")
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> getPopular(
        @PageableDefault(size = 20, sort = "salesCount", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(buyerProductService.getPopular(pageable)));
    }

    // 단건 상세: GET /api/products/{productId}
    @Operation(summary = "상품 단건 상세 조회")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getDetail(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(buyerProductService.getDetail(productId)));
    }

    // 연관 상품: GET /api/products/{productId}/related
    @Operation(summary = "연관 상품 조회")
    @GetMapping("/{productId}/related")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getRelated(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(buyerProductService.getRelated(productId)));
    }
}
