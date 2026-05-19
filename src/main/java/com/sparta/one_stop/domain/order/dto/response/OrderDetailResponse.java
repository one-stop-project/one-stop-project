package com.sparta.one_stop.domain.order.dto.response;

import com.sparta.one_stop.global.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(

    Long orderId,

    OrderStatus status,

    Long deliveryFee,

    List<OrderDetailItemResponse> orderItems,

    ReceiverResponse receiver,

    Long totalPrice,

    Long discountPrice,

    Integer usedPoint,

    Long finalPrice,

    LocalDateTime createdAt
) {
}
