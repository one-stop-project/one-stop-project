package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProductService {

    private final ProductRepository productRepository;

    // 승인 요청된 상품 목록 조회
    public List<Product> getPendingProducts() {
        return productRepository.findAllByStatus(ProductStatus.APPROVE_REQUESTED);
    }

    // 상품 승인
    @Transactional
    public void approveProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        if (product.getStatus() == ProductStatus.APPROVED) {
            throw new CustomException(ErrorCode.ADMIN_002);
        }

        product.approve();
    }

    // 상품 반려
    @Transactional
    public void rejectProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        if (product.getStatus() == ProductStatus.REJECTED) {
            throw new CustomException(ErrorCode.ADMIN_003);
        }

        product.reject();
    }

    // 상품 강제 비활성화
    @Transactional
    public void forceInactiveProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        if (product.getStatus() == ProductStatus.FORCE_INACTIVE) {
            throw new CustomException(ErrorCode.ADMIN_003);
        }

        product.forceInactive();
    }
}
