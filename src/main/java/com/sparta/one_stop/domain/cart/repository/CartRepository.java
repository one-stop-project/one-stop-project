package com.sparta.one_stop.domain.cart.repository;

import com.sparta.one_stop.domain.cart.entity.Cart;
import com.sparta.one_stop.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // 유저의 장바구니 조회
    Optional<Cart> findByUser(User user);

    // userId로 장바구니 조회
    Optional<Cart> findByUserId(Long userId);

    // 유저 장바구니 존재 여부 확인
    boolean existsByUser(User user);

}
