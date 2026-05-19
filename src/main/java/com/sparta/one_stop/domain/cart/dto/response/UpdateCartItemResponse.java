package com.sparta.one_stop.domain.cart.dto.response;

public record UpdateCartItemResponse(

    Long cartItemId,

    Integer quantity
) {

    public static UpdateCartItemResponse of(
        Long cartItemId,
        Integer quantity
    ) {

        return new UpdateCartItemResponse(
            cartItemId,
            quantity
        );
    }
}
