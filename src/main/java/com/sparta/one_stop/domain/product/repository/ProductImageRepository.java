package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.ProductImage;
import com.sparta.one_stop.global.enums.product.ProductImageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    // 상품의 특정 상태 이미지를 display_order 오름차순으로 조회
    List<ProductImage> findByProductIdAndStatusOrderByDisplayOrderAsc(
            Long productId, ProductImageStatus status);
}
