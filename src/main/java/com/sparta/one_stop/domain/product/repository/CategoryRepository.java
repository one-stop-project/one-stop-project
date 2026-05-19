package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // 여러 카테고리 ID로 한 번에 조회
    List<Category> findAllByIdIn(List<Long> ids);
}
