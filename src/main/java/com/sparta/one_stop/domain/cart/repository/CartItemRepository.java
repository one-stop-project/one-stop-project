package com.sparta.one_stop.domain.cart.repository;

import com.sparta.one_stop.domain.cart.entity.Cart;
import com.sparta.one_stop.domain.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    // 장바구니 전체 상품 조회
    List<CartItem> findAllByCart(Cart cart);

    // cartId 기준 장바구니 상품 조회
    List<CartItem> findAllByCartId(Long cartId);

    // 동일 상품 옵션이 이미 장바구니에 존재하는지 조회
    Optional<CartItem> findByCartIdAndProductItemId(
        Long cartId,
        Long productItemId
    );

    // 특정 장바구니 상품 삭제
    void deleteByCartIdAndId(Long cartId, Long cartItemId);

}
