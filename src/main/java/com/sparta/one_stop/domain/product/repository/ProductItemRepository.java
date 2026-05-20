package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductItemRepository extends JpaRepository<ProductItem, Long> {

    // 재고 변경 시 비관적 락으로 조회 (동시성 제어)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ProductItem i WHERE i.id = :itemId")
    Optional<ProductItem> findByIdForUpdate(@Param("itemId") Long itemId);
}
