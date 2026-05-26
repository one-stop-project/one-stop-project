package com.sparta.one_stop.domain.cart.dto.response;

public record UpdateCartItemResponse(

    Long itemId,

    Integer quantity
) {

    public static UpdateCartItemResponse of(
        Long itemId,
        Integer quantity
    ) {

        return new UpdateCartItemResponse(
            itemId,
            quantity
        );
    }
}
