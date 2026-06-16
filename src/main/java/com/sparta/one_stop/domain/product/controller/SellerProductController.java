package com.sparta.one_stop.domain.product.controller;

import com.sparta.one_stop.domain.product.dto.request.ProductCreateRequest;
import com.sparta.one_stop.domain.product.dto.request.ProductUpdateRequest;
import com.sparta.one_stop.domain.product.dto.response.PopularTagResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductCreateResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductDeleteResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductImageAddResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductImageDeleteResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductImageThumbnailResponse;
import com.sparta.one_stop.domain.product.dto.response.SellerProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.SellerProductListResponse;
import com.sparta.one_stop.domain.product.service.PopularTagService;
import com.sparta.one_stop.domain.product.service.SellerProductService;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Seller - Product", description = "판매자 본인 상품 관리 API")
@RestController
@RequestMapping("/api/seller/products")
@RequiredArgsConstructor
public class SellerProductController {

    private static final int TAG_AUTOCOMPLETE_MAX_LIMIT = 10;

    private final SellerProductService sellerProductService;
    private final PopularTagService popularTagService;

    // 태그 자동완성: GET /api/seller/products/tags/popular?keyword=면&limit=10
    @Operation(summary = "인기 태그 자동완성 — 태그 입력 시 사용 빈도 기반 제안")
    @GetMapping("/tags/popular")
    public ResponseEntity<ApiResponse<List<PopularTagResponse>>> getPopularTags(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "10") int limit
    ) {
        if (limit < 1 || limit > TAG_AUTOCOMPLETE_MAX_LIMIT) {
            throw new CustomException(ErrorCode.PRODUCT_014,
                "limit은 1~" + TAG_AUTOCOMPLETE_MAX_LIMIT + " 범위여야 합니다 (요청 limit=" + limit + ")");
        }
        return ResponseEntity.ok(ApiResponse.success(popularTagService.getAutocompleteTags(keyword, limit)));
    }

    // 상품 등록 (multipart/form-data: data 파트 = 상품 JSON, images 파트 = 이미지 파일들)
    @Operation(summary = "상품 등록")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductCreateResponse>> create(
        @AuthenticationPrincipal AuthUser authUser,
        @Valid @RequestPart("data") ProductCreateRequest request,
        @RequestPart("images") List<MultipartFile> images
    ) {
        ProductCreateResponse response = sellerProductService.create(authUser.userId(), request, images);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    // 상품 목록 조회 (판매자 본인의 상품)
    @Operation(summary = "내 상품 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<SellerProductListResponse>>> getMyProducts(
        @AuthenticationPrincipal AuthUser authUser,
        @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SellerProductListResponse> response =
            sellerProductService.getMyProducts(authUser.userId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 상품 단건 상세 조회 (판매자 본인) — 미승인 상품·판매중단 옵션·재고 포함
    @Operation(summary = "내 상품 단건 상세 조회")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<SellerProductDetailResponse>> getMyProductDetail(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long productId
    ) {
        SellerProductDetailResponse response =
            sellerProductService.getMyProductDetail(authUser.userId(), productId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 상품 수정
    @Operation(summary = "상품 수정")
    @PatchMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> update(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long productId,
        @Valid @RequestBody ProductUpdateRequest request
    ) {
        ProductDetailResponse response =
            sellerProductService.update(authUser.userId(), productId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 상품 삭제 (Soft Delete)
    @Operation(summary = "상품 삭제 (Soft Delete)")
    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductDeleteResponse>> delete(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long productId
    ) {
        ProductDeleteResponse response = sellerProductService.delete(authUser.userId(), productId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 상품 이미지 삭제 (Soft Delete)
    @Operation(summary = "상품 이미지 삭제 (Soft Delete)")
    @DeleteMapping("/{productId}/images/{imageId}")
    public ResponseEntity<ApiResponse<ProductImageDeleteResponse>> deleteImage(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long productId,
        @PathVariable Long imageId
    ) {
        ProductImageDeleteResponse response =
            sellerProductService.deleteImage(authUser.userId(), productId, imageId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 상품 이미지 추가 (multipart/form-data: images 파트 = 이미지 파일들)
    @Operation(summary = "상품 이미지 추가")
    @PostMapping(value = "/{productId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProductImageAddResponse>> addImages(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long productId,
        @RequestPart("images") List<MultipartFile> images
    ) {
        ProductImageAddResponse response =
            sellerProductService.addImages(authUser.userId(), productId, images);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    // 상품 대표 이미지(썸네일) 변경
    @Operation(summary = "상품 대표 이미지(썸네일) 변경")
    @PatchMapping("/{productId}/images/{imageId}/thumbnail")
    public ResponseEntity<ApiResponse<ProductImageThumbnailResponse>> changeThumbnail(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long productId,
        @PathVariable Long imageId
    ) {
        ProductImageThumbnailResponse response =
            sellerProductService.changeThumbnail(authUser.userId(), productId, imageId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
