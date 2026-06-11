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

    // 재고 변경 시 비관적 락으로 단건 조회 (동시성 제어)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ProductItem i WHERE i.id = :itemId")
    Optional<ProductItem> findByIdForUpdate(@Param("itemId") Long itemId);

    // 주문 생성 시 재고 차감 대상 상품 옵션을 비관적 락으로 일괄 조회
    // @Lock(PESSIMISTIC_WRITE)를 통해 트랜잭션 내에서 SELECT FOR UPDATE 계열 잠금을 획득한다.
    // item_id ASC 순서로 락을 획득하여 다중 상품 주문 시 데드락 가능성을 줄인다.
    // Product / Seller까지 fetch join하여 주문 가능 여부 검증 및 OrderItem 스냅샷 생성 시 N+1을 방지한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select pi
        from ProductItem pi
        join fetch pi.product p
        join fetch p.seller
        where pi.id in :itemIds
        order by pi.id asc
    """)
    List<ProductItem> findAllByIdInForUpdate(
        @Param("itemIds") List<Long> itemIds
    );

    // Redis 분산 락 테스트용 — DB 비관적 락 없이 조회 (락은 상위 레이어에서 처리)
    @Query("""
        select pi
        from ProductItem pi
        join fetch pi.product p
        join fetch p.seller
        where pi.id in :itemIds
        order by pi.id asc
    """)
    List<ProductItem> findAllByIdInNoLock(
        @Param("itemIds") List<Long> itemIds
    );

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
