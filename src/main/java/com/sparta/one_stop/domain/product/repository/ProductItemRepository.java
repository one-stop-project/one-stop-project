package com.sparta.one_stop.domain.product.repository;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 주문 취소 시 상품 옵션 재고를 DB 레벨에서 원자적으로 복구한다.
     *
     * 주의:
     * - JPA dirty checking 기반 increaseStock()은 엔티티를 읽은 시점의 stock 값에 수량을 더한 뒤
     *   커밋 시 절대값 SET update를 실행할 수 있다.
     * - 그 사이 다른 주문 생성 트랜잭션이 같은 ProductItem의 stock을 변경하면
     *   변경분이 덮어씌워질 수 있다.
     * - 따라서 주문 취소 재고 복구는 bulk update로 stock = stock + qty 형태의
     *   DB 원자 연산을 사용한다.
     *
     * 반환값:
     * - update된 row 수
     */
    @Modifying(flushAutomatically = true)
    @Query("""
        update ProductItem pi
        set pi.stock = pi.stock + :qty
        where pi.id = :id
    """)
    int increaseStockById(
        @Param("id") Long id,
        @Param("qty") int qty
    );

}
