package com.sparta.one_stop.domain.product.controller;

import com.sparta.one_stop.domain.product.dto.response.CacheableProductList;
import com.sparta.one_stop.domain.product.dto.response.PopularKeywordResponse;
import com.sparta.one_stop.domain.product.dto.response.PopularProductResponse;
import com.sparta.one_stop.domain.product.dto.response.BuyerProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductSummaryResponse;
import com.sparta.one_stop.domain.product.service.BuyerProductService;
import com.sparta.one_stop.domain.product.service.PopularKeywordService;
import com.sparta.one_stop.domain.product.service.PopularProductService;
import com.sparta.one_stop.global.enums.product.SortType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    private static final int MIN_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 20;
    private static final int POPULAR_MAX_LIMIT = 20;
    private static final int POPULAR_KEYWORD_MAX_LIMIT = 10;

    private final BuyerProductService buyerProductService;
    private final PopularProductService popularProductService;
    private final PopularKeywordService popularKeywordService;

    // 검색/목록: GET /api/products?keyword=&categoryId=&minPrice=&maxPrice=&sort=&page=&size=
    // 캐시 hit이어도 인기검색어 집계는 매번 — 컨트롤러에서 호출
    @Operation(summary = "상품 검색/목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> search(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Long minPrice,
        @RequestParam(required = false) Long maxPrice,
        @RequestParam(name = "sort", required = false) String sortParam,
        @PageableDefault(size = 20) Pageable pageable,
        @AuthenticationPrincipal AuthUser authUser
    ) {
        SortType sort = parseSortType(sortParam);
        validatePageSize(pageable);
        validatePriceRange(minPrice, maxPrice);
        CacheableProductList list = buyerProductService.search(keyword, categoryId, minPrice, maxPrice, sort, pageable);
        Page<ProductSummaryResponse> page = new PageImpl<>(
            list.content(), PageRequest.of(list.page(), list.size()), list.total()
        );
        Long userId = authUser != null ? authUser.userId() : null;
        popularKeywordService.recordKeyword(keyword, userId);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    private void validatePriceRange(Long minPrice, Long maxPrice) {
        if (minPrice != null && minPrice < 0) {
            throw new CustomException(ErrorCode.PRODUCT_015, "minPrice는 0 이상이어야 합니다");
        }
        if (maxPrice != null && maxPrice < 0) {
            throw new CustomException(ErrorCode.PRODUCT_015, "maxPrice는 0 이상이어야 합니다");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new CustomException(ErrorCode.PRODUCT_015, "minPrice가 maxPrice보다 클 수 없습니다");
        }
    }

    // sort 파라미터를 SortType으로 안전 변환 — 누락/잘못된 값은 기본 정렬(LATEST)로 (검색이 깨지지 않게)
    // toUpperCase는 Locale.ROOT 고정 (기본 Locale 의존 시 일부 환경에서 정상값도 폴백되는 것 방지)
    static SortType parseSortType(String sort) {
        if (sort == null || sort.isBlank()) {
            return SortType.LATEST;
        }
        try {
            return SortType.valueOf(sort.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SortType.LATEST;
        }
    }

    // 인기 상품: GET /api/products/popular — /{productId} 보다 먼저 선언해야 우선 매핑됨
    @Operation(summary = "인기 상품 조회")
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<PopularProductResponse>>> getPopular(
        @RequestParam(defaultValue = "20") int limit
    ) {
        validatePopularLimit(limit);
        return ResponseEntity.ok(ApiResponse.success(popularProductService.getPopular(limit)));
    }

    // 인기 검색어: GET /api/products/popular-keywords — /{productId} 보다 먼저 선언해야 우선 매핑됨
    @Operation(summary = "인기 검색어 조회")
    @GetMapping("/popular-keywords")
    public ResponseEntity<ApiResponse<List<PopularKeywordResponse>>> getPopularKeywords(
        @RequestParam(defaultValue = "10") int limit
    ) {
        validatePopularKeywordLimit(limit);
        return ResponseEntity.ok(ApiResponse.success(popularKeywordService.getPopularKeywords(limit)));
    }

    private void validatePageSize(Pageable pageable) {
        int size = pageable.getPageSize();
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.PRODUCT_014,
                "page size는 " + MIN_PAGE_SIZE + "~" + MAX_PAGE_SIZE + " 범위여야 합니다 (요청 size=" + size + ")");
        }
    }

    private void validatePopularLimit(int limit) {
        if (limit < 1 || limit > POPULAR_MAX_LIMIT) {
            throw new CustomException(ErrorCode.PRODUCT_014,
                "limit은 1~" + POPULAR_MAX_LIMIT + " 범위여야 합니다 (요청 limit=" + limit + ")");
        }
    }

    private void validatePopularKeywordLimit(int limit) {
        if (limit < 1 || limit > POPULAR_KEYWORD_MAX_LIMIT) {
            throw new CustomException(ErrorCode.PRODUCT_014,
                "limit은 1~" + POPULAR_KEYWORD_MAX_LIMIT + " 범위여야 합니다 (요청 limit=" + limit + ")");
        }
    }

    // 단건 상세: GET /api/products/{productId}
    // 응답은 캐시(productDetail 10분), 조회수는 캐시와 무관하게 매 요청마다 카운트
    @Operation(summary = "상품 단건 상세 조회")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<BuyerProductDetailResponse>> getDetail(
        @PathVariable Long productId,
        @AuthenticationPrincipal AuthUser authUser
    ) {
        Long userId = authUser != null ? authUser.userId() : null;
        BuyerProductDetailResponse response = buyerProductService.getDetail(productId);
        buyerProductService.recordView(productId, userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 연관 상품: GET /api/products/{productId}/related
    @Operation(summary = "연관 상품 조회")
    @GetMapping("/{productId}/related")
    public ResponseEntity<ApiResponse<List<ProductSummaryResponse>>> getRelated(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success(buyerProductService.getRelated(productId)));
    }
}
