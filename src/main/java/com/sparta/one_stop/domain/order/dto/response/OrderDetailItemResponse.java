package com.sparta.one_stop.domain.order.dto.response;

import com.sparta.one_stop.global.enums.OrderItemStatus;

public record OrderDetailItemResponse(

    Long orderItemId,

    Long itemId,

    String itemName,

    Long sellerId,

    Integer quantity,

    Long price,

    OrderItemStatus status,

    DeliverySummaryResponse delivery
) {
}
