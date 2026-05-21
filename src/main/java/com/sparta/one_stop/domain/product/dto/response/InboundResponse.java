package com.sparta.one_stop.domain.product.dto.response;

public record InboundResponse(
        Long itemId,
        Long previousStock,
        Long addedQuantity,
        Long currentStock
) {
}
