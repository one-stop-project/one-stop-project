package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.entity.AdminActionHistory;
import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.global.enums.admin.AdminActionTarget;
import com.sparta.one_stop.global.enums.admin.AdminActionType;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductService {

    private final ProductRepository productRepository;
    private final AdminActionHistoryRepository adminActionHistoryRepository;

    // 승인 요청된 상품 목록 조회
    public Page<Product> getPendingProducts(Pageable pageable) {
        return productRepository.findAllByStatus(ProductStatus.APPROVE_REQUESTED, pageable);
    }

    // 상품 승인
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "productDetail", key = "#productId", cacheManager = "redisCacheManager"),
        @CacheEvict(value = "productList", allEntries = true, cacheManager = "redisCacheManager")
    })
    public void approveProduct(Long productId, Long actorId) {
        Product product = findProductOrThrow(productId);

        if (product.getStatus() == ProductStatus.APPROVED) {
            throw new CustomException(ErrorCode.ADMIN_008);
        }

        product.approve();

        // 승인 이력 저장 (reason 없음)
        adminActionHistoryRepository.save(AdminActionHistory.builder()
            .actorId(actorId)
            .targetType(AdminActionTarget.PRODUCT)
            .targetId(productId)
            .action(AdminActionType.APPROVE)
            .build());
    }

    // 상품 반려
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "productDetail", key = "#productId", cacheManager = "redisCacheManager"),
        @CacheEvict(value = "productList", allEntries = true, cacheManager = "redisCacheManager")
    })
    public void rejectProduct(Long productId, Long actorId, String reason) {
        Product product = findProductOrThrow(productId);

        if (product.getStatus() == ProductStatus.REJECTED) {
            throw new CustomException(ErrorCode.ADMIN_009);
        }

        product.reject();

        // 반려 이력 저장 (reason 필수)
        adminActionHistoryRepository.save(AdminActionHistory.builder()
            .actorId(actorId)
            .targetType(AdminActionTarget.PRODUCT)
            .targetId(productId)
            .action(AdminActionType.REJECT)
            .reason(reason)
            .build());
    }

    // 상품 강제 비활성화
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "productDetail", key = "#productId", cacheManager = "redisCacheManager"),
        @CacheEvict(value = "productList", allEntries = true, cacheManager = "redisCacheManager")
    })
    public void forceInactiveProduct(Long productId, Long actorId, String reason) {
        Product product = findProductOrThrow(productId);

        if (product.getStatus() == ProductStatus.FORCE_INACTIVE) {
            throw new CustomException(ErrorCode.ADMIN_007);
        }

        product.forceInactive();

        // 강제비활성화 이력 저장 (reason 필수)
        adminActionHistoryRepository.save(AdminActionHistory.builder()
            .actorId(actorId)
            .targetType(AdminActionTarget.PRODUCT)
            .targetId(productId)
            .action(AdminActionType.FORCE_INACTIVE)
            .reason(reason)
            .build());
    }

    // 상품 조회 공통 메서드
    private Product findProductOrThrow(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));
    }
}
