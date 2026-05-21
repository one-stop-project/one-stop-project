package com.sparta.one_stop.domain.order.dto.response;

import com.sparta.one_stop.global.enums.order.OrderStatus;

public record CancelOrderResponse(

    Long orderId,

    OrderStatus status,

    Long refundAmount,

    Integer restoredPoint,

    RestoredCouponResponse restoredCoupon
) {
}
