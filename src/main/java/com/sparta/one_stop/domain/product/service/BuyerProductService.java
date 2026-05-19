package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.dto.response.ProductDetailResponse;
import com.sparta.one_stop.domain.product.dto.response.ProductSummaryResponse;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuyerProductService {

    private static final int RELATED_PRODUCT_LIMIT = 8;

    private final ProductRepository productRepository;

    // GET /api/products — 검색/목록
    public Page<ProductSummaryResponse> search(String keyword, Long categoryId, Pageable pageable) {
        String kw = (keyword != null && !keyword.isBlank()) ? keyword : null;
        return productRepository.searchApproved(
            ProductStatus.APPROVED, SellerStatus.APPROVED, kw, categoryId, pageable
        ).map(ProductSummaryResponse::from);
    }

    // GET /api/products/{productId} — 단건 상세
    @Transactional
    public ProductDetailResponse getDetail(Long productId) {
        Product product = findApprovedProduct(productId);
        productRepository.incrementViewCount(productId);
        return ProductDetailResponse.from(product);
    }

    // GET /api/products/{productId}/related — 연관 상품
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

    // GET /api/products/popular — 인기 상품 (salesCount DESC)
    public Page<ProductSummaryResponse> getPopular(Pageable pageable) {
        return productRepository.findApproved(
            ProductStatus.APPROVED, SellerStatus.APPROVED, pageable
        ).map(ProductSummaryResponse::from);
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
