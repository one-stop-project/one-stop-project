package com.sparta.one_stop.domain.ai.repository;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiProductReader extends JpaRepository<Product, Long> {

    // 같은 카테고리 내 상품 ID 조회 (자기 자신 제외, 인기순)
    @Query("""
        SELECT DISTINCT p.id FROM Product p
        JOIN p.categoryMappings m
        WHERE m.category.id IN :categoryIds
        AND p.id != :excludeId
        AND p.status = :status
        ORDER BY p.salesCount DESC
        """)
    List<Long> findIdsByCategoryIds(
        @Param("categoryIds") List<Long> categoryIds,
        @Param("excludeId") Long excludeId,
        @Param("status") ProductStatus status,
        Pageable pageable
    );

    // productItems 포함해서 로딩 (N+1 방지)
    @EntityGraph(attributePaths = {"productItems", "seller"})
    @Query("SELECT p FROM Product p WHERE p.id IN :ids")
    List<Product> findWithItemsByIds(@Param("ids") List<Long> ids);
}
