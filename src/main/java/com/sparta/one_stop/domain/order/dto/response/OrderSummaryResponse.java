package com.sparta.one_stop.domain.order.dto.response;

import com.sparta.one_stop.global.enums.OrderStatus;

import java.time.LocalDateTime;

public record OrderSummaryResponse(

    Long orderId,

    Long finalPrice,

    OrderStatus status,

    Integer itemCount,

    String firstItemName,

    String firstItemThumbnail,

    LocalDateTime createdAt
) {
}
