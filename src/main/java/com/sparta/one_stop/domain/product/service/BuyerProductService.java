package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.dto.response.ProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductSummaryResponse;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.product.SortType;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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

    // sort=POPULAR은 ZSET 순서 그대로 반환 (keyword/categoryId 필터 미적용)
    public Page<ProductSummaryResponse> search(String keyword, Long categoryId, SortType sort, Pageable pageable) {
        if (sort == SortType.POPULAR) {
            return searchPopular(pageable);
        }

        String kw = (keyword != null && !keyword.isBlank()) ? keyword : null;
        Pageable sorted = applySorting(pageable, sort);
        return productRepository.searchApproved(
            ProductStatus.APPROVED, SellerStatus.APPROVED, kw, categoryId, sorted
        ).map(ProductSummaryResponse::from);
    }

    private Page<ProductSummaryResponse> searchPopular(Pageable pageable) {
        // 인기 상품 ZSET은 TOP N(상한 20)만 보관
        if (pageable.getPageNumber() > 0) {
            return Page.empty(pageable);
        }

        int limit = pageable.getPageSize();
        List<Long> popularIds = popularProductService.getPopularProductIds(limit);

        // Redis ZSET 비어있음/장애 → DB fallback (sales_count DESC)
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

    private Pageable applySorting(Pageable pageable, SortType sort) {
        Sort sortBy = switch (sort) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "id");
            // 가격 정렬은 product_item.price 기반 MIN 정렬이 필요해 추후 구현
            // 현재는 명시적 400으로 사용자에게 미지원 사실 전달
            case PRICE_ASC, PRICE_DESC -> throw new CustomException(
                ErrorCode.COMMON_001,
                sort + " 정렬은 아직 지원되지 않습니다 (LATEST 또는 POPULAR 사용)");
            case POPULAR -> Sort.unsorted();
        };
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortBy);
    }

    public ProductDetailResponse getDetail(Long productId, Long userId) {
        Product product = findApprovedProduct(productId);
        viewCountService.recordView(productId, userId);
        return ProductDetailResponse.from(product);
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
