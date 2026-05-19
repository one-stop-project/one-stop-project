package com.sparta.one_stop.domain.product.controller;

import com.sparta.one_stop.domain.product.dto.request.ProductCreateRequest;
import com.sparta.one_stop.domain.product.dto.request.ProductUpdateRequest;
import com.sparta.one_stop.domain.product.dto.response.ProductCreateResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductDeleteResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.SellerProductListResponse;
import com.sparta.one_stop.domain.product.service.SellerProductService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller/products")
@RequiredArgsConstructor
public class SellerProductController {

    private final SellerProductService sellerProductService;

    // 상품 등록
    @PostMapping
    public ResponseEntity<ApiResponse<ProductCreateResponse>> create(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody ProductCreateRequest request
    ) {
        ProductCreateResponse response = sellerProductService.create(userDetails.getUserId(), request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    // 상품 목록 조회 (판매자 본인의 상품)
    @GetMapping
    public ResponseEntity<ApiResponse<Page<SellerProductListResponse>>> getMyProducts(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SellerProductListResponse> response =
            sellerProductService.getMyProducts(userDetails.getUserId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 상품 수정
    @PatchMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> update(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long productId,
        @Valid @RequestBody ProductUpdateRequest request
    ) {
        ProductDetailResponse response =
            sellerProductService.update(userDetails.getUserId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 상품 삭제 (Soft Delete)
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDeleteResponse>> delete(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long productId
    ) {
        ProductDeleteResponse response = sellerProductService.delete(userDetails.getUserId(), productId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
