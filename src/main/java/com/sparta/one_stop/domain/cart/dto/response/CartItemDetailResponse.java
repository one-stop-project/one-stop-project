package com.sparta.one_stop.domain.cart.dto.response;

import com.sparta.one_stop.domain.cart.entity.CartItem;
import com.sparta.one_stop.domain.product.entity.ProductItem;

public record CartItemDetailResponse(

    // 로그인 장바구니의 DB cart_item_id
    // 비로그인 장바구니는 DB에 저장되지 않으므로 null
    Long cartItemId,

    // 상품 옵션 ID
    // 로그인/비로그인 장바구니 수정/삭제 API의 기준값
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

    public static CartItemDetailResponse ofGuest(
        ProductItem productItem,
        Integer quantity
    ) {
        return new CartItemDetailResponse(
            null,
            productItem.getId(),
            productItem.getProduct().getId(),
            productItem.getProduct().getName(),
            productItem.getOptionSummary(),
            productItem.getPrice(),
            quantity,
            productItem.getProduct().getThumbnailUrl(),
            productItem.getStock(),
            productItem.getStock() > 0 && productItem.isOnSale()
        );
    }

}
