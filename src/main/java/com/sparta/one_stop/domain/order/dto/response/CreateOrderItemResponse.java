package com.sparta.one_stop.domain.order.dto.response;

import com.sparta.one_stop.global.enums.OrderItemStatus;

public record CreateOrderItemResponse(

    Long orderItemId,

    Long itemId,

    String itemName,

    Long price,

    Integer quantity,

    Long subtotal,

    String sellerName,

    OrderItemStatus status
) {
}
