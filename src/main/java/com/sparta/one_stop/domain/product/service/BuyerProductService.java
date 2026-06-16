package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.dto.response.CacheableProductList;
import com.sparta.one_stop.domain.product.dto.response.BuyerProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductSummaryResponse;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.product.repository.ProductSearchCond;
import com.sparta.one_stop.global.enums.product.ProductItemStatus;
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
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuyerProductService {

    private static final int RELATED_PRODUCT_LIMIT = 10;
    // 인기 상품 보관 상한 — PopularProductService.TOP_N(20)과 동일. 이 범위 안에서 페이지네이션한다.
    private static final int POPULAR_MAX = 20;

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
            sanitizeKeyword(keyword), categoryId, minPrice, maxPrice, sort, null
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
        // 보관된 상위 N개(TOP 20)를 한 번에 추려두고 요청 페이지로 슬라이스한다.
        // size<20이어도 page를 넘기면 11~20위까지 도달 가능 (page>0를 무조건 빈 결과로 막던 버그 수정).
        List<ProductSummaryResponse> ranked = rankedPopular();

        List<ProductSummaryResponse> content = ranked.stream()
            .skip(pageable.getOffset())
            .limit(pageable.getPageSize())
            .toList();

        return new PageImpl<>(content, pageable, ranked.size());
    }

    // 인기 상위 N개 중 노출 가능(승인·판매중 옵션 보유) 상품을 랭킹 순서로 반환.
    // Redis 랭킹이 비었거나 장애면 DB 판매수 상위 N개 ID로 대체한다.
    private List<ProductSummaryResponse> rankedPopular() {
        List<Long> popularIds = popularProductService.getPopularProductIds(POPULAR_MAX);

        if (popularIds.isEmpty()) {
            popularIds = productRepository.findApproved(
                    ProductStatus.APPROVED, SellerStatus.APPROVED,
                    PageRequest.of(0, POPULAR_MAX, Sort.by(Sort.Direction.DESC, "salesCount")))
                .stream().map(Product::getId).toList();
        }
        if (popularIds.isEmpty()) {
            return List.of();
        }

        // 폴백·정상 경로 공통: 노출 가능(승인·판매자 승인·판매중 옵션 보유) 상품만 랭킹 순서로.
        // 폴백도 isVisibleOnSale 필터를 거쳐 전 옵션 STOP인 상품의 0원 노출을 막는다.
        Map<Long, Product> productMap = productRepository.findAllByIdsWithItems(popularIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        return popularIds.stream()
            .map(productMap::get)
            .filter(Objects::nonNull)
            .filter(Product::isVisibleOnSale)
            .map(ProductSummaryResponse::from)
            .toList();
    }

    // 단건 응답만 캐시 (10분)
    // 조회수는 캐시 hit이어도 매번 세야 해서 컨트롤러에서 따로 호출
    @Cacheable(value = "productDetail", key = "#productId", cacheManager = "redisCacheManager")
    public BuyerProductDetailResponse getDetail(Long productId) {
        Product product = findApprovedProduct(productId);
        return BuyerProductDetailResponse.from(product);
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

        // 정렬은 쿼리 ORDER BY(조회수 70% + 판매수 30%)에서 처리 → Pageable엔 페이지·크기만
        Pageable limit = PageRequest.of(0, RELATED_PRODUCT_LIMIT);
        return productRepository.findRelated(
            categoryIds, productId, ProductStatus.APPROVED, SellerStatus.APPROVED,
            ProductItemStatus.ON_SALE, limit
        ).stream().map(ProductSummaryResponse::from).toList();
    }

    private Product findApprovedProduct(Long productId) {
        Product product = productRepository.findWithCollectionsById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        // 승인·판매자 승인뿐 아니라 판매중(ON_SALE) 옵션이 최소 1개 있어야 노출
        // (전 옵션 STOP인 상품이 0원·빈 옵션 상세로 노출되는 것 방지)
        if (!product.isVisibleOnSale()) {
            throw new CustomException(ErrorCode.PRODUCT_002);
        }
        return product;
    }
}
