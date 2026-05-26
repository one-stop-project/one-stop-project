package com.sparta.one_stop.domain.cart.repository;

import com.sparta.one_stop.domain.cart.entity.Cart;
import com.sparta.one_stop.domain.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // 장바구니 전체 상품 조회
    List<CartItem> findAllByCart(Cart cart);

    // cartId 기준 장바구니 상품 조회
    List<CartItem> findAllByCartId(Long cartId);

    // 장바구니 조회용
    // CartItem → ProductItem → Product를 한 번에 조회하여 DTO 변환 시 N+1 방지
    @Query("""
        select ci
        from CartItem ci
        join fetch ci.productItem pi
        join fetch pi.product p
        where ci.cart.id = :cartId
    """)
    List<CartItem> findAllByCartIdWithProduct(
        @Param("cartId") Long cartId
    );

    // 동일 상품 옵션이 이미 장바구니에 존재하는지 조회
    Optional<CartItem> findByCartIdAndProductItemId(
        Long cartId,
        Long productItemId
    );

    // 특정 장바구니 상품 삭제
    void deleteByCartIdAndId(Long cartId, Long cartItemId);

    // 장바구니에 담긴 상품 종류 수 조회
    long countByCartId(Long cartId);

}
