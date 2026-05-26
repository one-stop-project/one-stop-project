package com.sparta.one_stop.domain.cart.dto.response;

import com.sparta.one_stop.domain.cart.entity.CartItem;

public record CartItemResponse(

    // 로그인 장바구니는 cartItemId 존재
    // 비로그인 장바구니는 cartItemId가 없으므로 null 반환
    Long cartItemId,

    // 상품 옵션 ID
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

    public static CartItemResponse ofGuest(
        Long itemId,
        Integer quantity
    ) {
        return new CartItemResponse(
            null,
            itemId,
            quantity,
            "장바구니에 추가되었습니다."
        );
    }

}
