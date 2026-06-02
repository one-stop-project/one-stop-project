package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.dto.response.CacheableProductList;
import com.sparta.one_stop.domain.product.dto.response.ProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductSummaryResponse;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.product.repository.ProductSearchCond;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.product.SortType;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuyerProductService {

    private static final int RELATED_PRODUCT_LIMIT = 8;

    private final ProductRepository productRepository;
    private final ProductViewCountService viewCountService;
    private final PopularProductService popularProductService;

    // 인기순 정렬은 랭킹 순서 그대로 반환 (keyword/categoryId/가격 필터 미적용)
    // 응답 캐시 5분 — 키는 모든 필터 조합
    // Page는 그대로 캐시 직렬화가 안 돼서 CacheableProductList에 담아 저장
    @Cacheable(
        value = "productList",
        key = "T(java.util.Objects).toString(#keyword,'') + ':' + T(java.util.Objects).toString(#categoryId,'') + ':' + " +
              "T(java.util.Objects).toString(#minPrice,'') + ':' + T(java.util.Objects).toString(#maxPrice,'') + ':' + " +
              "#sort + ':' + #pageable.pageNumber + ':' + #pageable.pageSize",
        cacheManager = "redisCacheManager",
        condition = "#sort != T(com.sparta.one_stop.global.enums.product.SortType).POPULAR"
    )
    public CacheableProductList search(
        String keyword, Long categoryId, Long minPrice, Long maxPrice, SortType sort, Pageable pageable
    ) {
        Page<ProductSummaryResponse> page = doSearch(keyword, categoryId, minPrice, maxPrice, sort, pageable);
        return new CacheableProductList(
            page.getContent(), pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements()
        );
    }

    private Page<ProductSummaryResponse> doSearch(
        String keyword, Long categoryId, Long minPrice, Long maxPrice, SortType sort, Pageable pageable
    ) {
        if (sort == SortType.POPULAR) {
            return searchPopular(pageable);
        }

        // 정렬은 QueryDSL 쿼리 내부에서 처리하므로 페이지 번호/크기만 전달
        Pageable plainPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        ProductSearchCond cond = new ProductSearchCond(
            ProductStatus.APPROVED, SellerStatus.APPROVED,
            sanitizeKeyword(keyword), categoryId, minPrice, maxPrice, sort
        );
        return productRepository.search(cond, plainPage).map(ProductSummaryResponse::from);
    }

    // FULLTEXT BOOLEAN MODE 연산자 문자(+ - ~ * " ( ) < > @)는 평문 검색이라 제거
    // 정리 후 비면 null → 키워드 필터 미적용(검색어 없음 = 전체 노출 유지)
    private String sanitizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String cleaned = keyword.replaceAll("[+\\-~*\"()<>@]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private Page<ProductSummaryResponse> searchPopular(Pageable pageable) {
        // 인기 상품은 상위 20개만 보관 → 2페이지부터는 빈 결과
        if (pageable.getPageNumber() > 0) {
            return Page.empty(pageable);
        }

        int limit = pageable.getPageSize();
        List<Long> popularIds = popularProductService.getPopularProductIds(limit);

        // 랭킹이 비었거나 Redis 장애면 DB에서 판매수 높은 순으로 대체
        if (popularIds.isEmpty()) {
            Pageable salesDesc = PageRequest.of(0, limit,
                Sort.by(Sort.Direction.DESC, "salesCount"));
            return productRepository.findApproved(
                ProductStatus.APPROVED, SellerStatus.APPROVED, salesDesc
            ).map(ProductSummaryResponse::from);
        }

        Map<Long, Product> productMap = productRepository.findAllByIdsWithItems(popularIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<ProductSummaryResponse> ordered = popularIds.stream()
            .map(productMap::get)
            .filter(this::isVisibleAndOnSale)
            .map(ProductSummaryResponse::from)
            .toList();

        return new PageImpl<>(ordered, pageable, ordered.size());
    }

    private boolean isVisibleAndOnSale(Product p) {
        if (p == null || !p.isApproved()) return false;
        if (p.getSeller().getStatus() != SellerStatus.APPROVED) return false;
        return p.getProductItems().stream().anyMatch(ProductItem::isOnSale);
    }

    // 단건 응답만 캐시 (10분)
    // 조회수는 캐시 hit이어도 매번 세야 해서 컨트롤러에서 따로 호출
    @Cacheable(value = "productDetail", key = "#productId", cacheManager = "redisCacheManager")
    public ProductDetailResponse getDetail(Long productId) {
        Product product = findApprovedProduct(productId);
        return ProductDetailResponse.from(product);
    }

    // 조회수 카운트 — 단건 조회 성공 후 컨트롤러에서 호출
    public void recordView(Long productId, Long userId) {
        viewCountService.recordView(productId, userId);
    }

    public List<ProductSummaryResponse> getRelated(Long productId) {
        Product product = findApprovedProduct(productId);

        List<Long> categoryIds = product.getCategoryMappings().stream()
            .map(m -> m.getCategory().getId())
            .toList();

        if (categoryIds.isEmpty()) {
            return List.of();
        }

        Pageable limit = PageRequest.of(0, RELATED_PRODUCT_LIMIT, Sort.by("salesCount").descending());
        return productRepository.findRelated(
            categoryIds, productId, ProductStatus.APPROVED, SellerStatus.APPROVED, limit
        ).stream().map(ProductSummaryResponse::from).toList();
    }

    private Product findApprovedProduct(Long productId) {
        Product product = productRepository.findWithCollectionsById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        if (!product.isApproved() || product.getSeller().getStatus() != SellerStatus.APPROVED) {
            throw new CustomException(ErrorCode.PRODUCT_002);
        }
        return product;
    }
}
