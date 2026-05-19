package com.sparta.one_stop.domain.cart.dto.response;

import com.sparta.one_stop.domain.cart.entity.CartItem;
import com.sparta.one_stop.domain.product.entity.ProductItem;

public record CartItemDetailResponse(

    Long cartItemId,

    Long itemId,

    Long productId,

    String productName,

    String optionName,

    Long price,

    Integer quantity,

    String thumbnailUrl,

    Long stock,

    boolean available
) {

    public static CartItemDetailResponse of(CartItem cartItem) {

        ProductItem productItem = cartItem.getProductItem();

        return new CartItemDetailResponse(
            cartItem.getId(),
            productItem.getId(),
            productItem.getProduct().getId(),
            productItem.getProduct().getName(),
            productItem.getOptionSummary(),
            productItem.getPrice(),
            cartItem.getQuantity(),
            productItem.getProduct().getThumbnailUrl(),
            productItem.getStock(),
            productItem.getStock() > 0 && productItem.isOnSale()
        );
    }
}
