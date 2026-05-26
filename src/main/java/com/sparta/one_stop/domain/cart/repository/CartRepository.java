package com.sparta.one_stop.domain.cart.repository;

import com.sparta.one_stop.domain.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // userId로 장바구니 조회
    Optional<Cart> findByUserId(Long userId);

}
