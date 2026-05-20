package com.sparta.one_stop.domain.product.dto.response;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.global.enums.product.ProductItemStatus;

public record ItemUpdateResponse(
        Long itemId,
        Long price,
        Long stock,
        ProductItemStatus status
) {
    public static ItemUpdateResponse from(ProductItem item) {
        return new ItemUpdateResponse(
                item.getId(),
                item.getPrice(),
                item.getStock(),
                item.getStatus()
        );
    }
}
