package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 상태별 상품 목록 조회 (관리자 승인/반려 목록용)
    List<Product> findAllByStatus(ProductStatus status);
}
