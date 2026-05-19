package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductItemRepository extends JpaRepository<ProductItem, Long> {
}
