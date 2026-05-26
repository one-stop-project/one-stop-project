package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductItemRepository extends JpaRepository<ProductItem, Long> {

    // 재고 변경 시 비관적 락으로 조회 (동시성 제어)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ProductItem i WHERE i.id = :itemId")
    Optional<ProductItem> findByIdForUpdate(@Param("itemId") Long itemId);

    // 비로그인 장바구니 조회용
    // Redis에는 itemId와 quantity만 저장되므로, 응답 생성에 필요한 ProductItem/Product 정보를 한 번에 조회
    // ProductItem → Product를 fetch join하여 DTO 변환 시 N+1 문제 방지
    @Query("""
        select pi
        from ProductItem pi
        join fetch pi.product p
        where pi.id in :itemIds
    """)
    List<ProductItem> findAllByIdInWithProduct(
        @Param("itemIds") List<Long> itemIds
    );

}
