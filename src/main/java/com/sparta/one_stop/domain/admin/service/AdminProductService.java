package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductService {

    private final ProductRepository productRepository;

    // 승인 요청된 상품 목록 조회 (페이징)
    public Page<Product> getPendingProducts(Pageable pageable) {
        return productRepository.findAllByStatus(ProductStatus.APPROVE_REQUESTED, pageable);
    }

    // 상품 승인
    @Transactional
    public void approveProduct(Long productId) {
        Product product = findProductOrThrow(productId);

        if (product.getStatus() == ProductStatus.APPROVED) {
            throw new CustomException(ErrorCode.ADMIN_008);
        }

        product.approve();
    }

    // 상품 반려
    @Transactional
    public void rejectProduct(Long productId) {
        Product product = findProductOrThrow(productId);

        if (product.getStatus() == ProductStatus.REJECTED) {
            throw new CustomException(ErrorCode.ADMIN_009);
        }

        product.reject();
    }

    // 상품 강제 비활성화
    @Transactional
    public void forceInactiveProduct(Long productId) {
        Product product = findProductOrThrow(productId);

        if (product.getStatus() == ProductStatus.FORCE_INACTIVE) {
            throw new CustomException(ErrorCode.ADMIN_007);
        }

        product.forceInactive();
    }

    // 상품 조회 공통 메서드
    private Product findProductOrThrow(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));
    }
}
