package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 상태별 상품 목록 조회 (관리자 승인/반려 목록용)
    List<Product> findAllByStatus(ProductStatus status);

    // 상태별 상품 목록 조회 - 페이징 (관리자 승인 대기 목록용)
    Page<Product> findAllByStatus(ProductStatus status, Pageable pageable);

    // 판매자 ID로 상품 목록 조회
    List<Product> findAllBySellerId(Long sellerId);

    // 판매자 ID로 상품 상태 일괄 변경
    @Modifying
    @Query("update Product p set p.status = :status where p.seller.id = :sellerId")
    int updateStatusBySellerId(@Param("sellerId") Long sellerId, @Param("status") ProductStatus status);


}
