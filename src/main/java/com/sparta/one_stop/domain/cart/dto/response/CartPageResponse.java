package com.sparta.one_stop.domain.cart.dto.response;

import java.util.List;

public record CartPageResponse(

    Long cartId,

    List<CartItemDetailResponse> content,

    Long totalPrice,

    Integer itemCount,

    int page,

    int size,

    long totalElements,

    int totalPages
) {

    public static CartPageResponse of(
        Long cartId,
        List<CartItemDetailResponse> content,
        Long totalPrice,
        Integer itemCount,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        return new CartPageResponse(
            cartId,
            content,
            totalPrice,
            itemCount,
            page,
            size,
            totalElements,
            totalPages
        );
    }

    public static CartPageResponse empty(
        int page,
        int size
    ) {
        return new CartPageResponse(
            null,
            List.of(),
            0L,
            0,
            page,
            size,
            0L,
            0
        );
    }

}
