package com.sparta.one_stop.domain.payment.dto.response;

import com.sparta.one_stop.global.enums.order.OrderStatus;

import java.time.LocalDateTime;

public record ApprovePaymentResponse(

    Long orderId,

    Long finalPrice,

    OrderStatus status,

    LocalDateTime paidAt
) {
}
