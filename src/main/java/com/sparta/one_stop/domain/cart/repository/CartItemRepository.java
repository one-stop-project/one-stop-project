package com.sparta.one_stop.domain.cart.repository;

import com.sparta.one_stop.domain.cart.entity.CartItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // 동일 상품 옵션이 이미 장바구니에 존재하는지 조회
    Optional<CartItem> findByCartIdAndProductItemId(
        Long cartId,
        Long productItemId
    );

    // 장바구니에 담긴 상품 종류 수 조회
    long countByCartId(Long cartId);

    // 장바구니 페이징 조회용
    // CartItem → ProductItem → Product를 한 번에 조회하여 DTO 변환 시 N+1 방지
    // totalPrice/itemCount는 별도 집계 쿼리로 전체 장바구니 기준 계산
    @Query(
        value = """
            select ci
            from CartItem ci
            join fetch ci.productItem pi
            join fetch pi.product p
            where ci.cart.id = :cartId
        """,
        countQuery = """
            select count(ci)
            from CartItem ci
            where ci.cart.id = :cartId
        """
    )
    Page<CartItem> findPageByCartIdWithProduct(
        @Param("cartId") Long cartId,
        Pageable pageable
    );

    // 전체 장바구니 상품 총액 조회
    @Query("""
        select coalesce(sum(ci.quantity * pi.price), 0)
        from CartItem ci
        join ci.productItem pi
        where ci.cart.id = :cartId
    """)
    Long sumTotalPriceByCartId(@Param("cartId") Long cartId);

    // 전체 장바구니 상품 수량 합계 조회
    @Query("""
        select coalesce(sum(ci.quantity), 0)
        from CartItem ci
        where ci.cart.id = :cartId
    """)
    Long sumQuantityByCartId(@Param("cartId") Long cartId);

}
