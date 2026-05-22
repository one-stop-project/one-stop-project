package com.sparta.one_stop.domain.cart.dto.response;

import com.sparta.one_stop.domain.cart.entity.Cart;
import com.sparta.one_stop.domain.cart.entity.CartItem;

import java.util.List;

public record CartResponse(

    Long cartId,

    List<CartItemDetailResponse> items,

    Long totalPrice,

    Integer itemCount
) {

    public static CartResponse of(
        Cart cart,
        List<CartItem> cartItems
    ) {

        List<CartItemDetailResponse> items =
            cartItems.stream()
                .map(CartItemDetailResponse::of)
                .toList();

        long totalPrice = cartItems.stream()
            .mapToLong(item ->
                item.getProductItem().getPrice() * item.getQuantity()
            )
            .sum();

        int itemCount = cartItems.stream()
            .mapToInt(CartItem::getQuantity)
            .sum();

        return new CartResponse(
            cart.getId(),
            items,
            totalPrice,
            itemCount
        );
    }

    // 아직 장바구니가 생성되지 않은 사용자를 위한 빈 응답
    public static CartResponse empty() {
        return new CartResponse(
            null,
            List.of(),
            0L,
            0
        );
    }

}
