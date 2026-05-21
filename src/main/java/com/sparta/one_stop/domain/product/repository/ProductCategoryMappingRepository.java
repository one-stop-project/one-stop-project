package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.ProductCategoryMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductCategoryMappingRepository extends JpaRepository<ProductCategoryMapping, Long> {

    // 카테고리 ID 목록 중 하나라도 상품 매핑이 존재하는지 검사 (삭제 가능 여부 판단)
    boolean existsByCategoryIdIn(List<Long> categoryIds);
}
