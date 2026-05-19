package com.sparta.one_stop.domain.cart.dto.response;

import com.sparta.one_stop.domain.cart.entity.CartItem;

public record CartItemResponse(

    Long cartItemId,

    Long itemId,

    Integer quantity,

    String message
) {

    public static CartItemResponse of(CartItem cartItem) {

        return new CartItemResponse(
            cartItem.getId(),
            cartItem.getProductItem().getId(),
            cartItem.getQuantity(),
            "장바구니에 추가되었습니다."
        );
    }
}
