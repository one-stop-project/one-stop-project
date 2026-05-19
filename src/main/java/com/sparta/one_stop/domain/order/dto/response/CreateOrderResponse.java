package com.sparta.one_stop.domain.order.dto.response;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.global.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record CreateOrderResponse(

    Long orderId,

    List<CreateOrderItemResponse> orderItems,

    Long totalPrice,

    Long discountPrice,

    Integer usedPoint,

    Long finalPrice,

    OrderStatus status,

    LocalDateTime createdAt
) {

    public static CreateOrderResponse of(
        Order order,
        List<CreateOrderItemResponse> orderItems
    ) {
        return new CreateOrderResponse(
            order.getId(),
            orderItems,
            order.getTotalPrice(),
            order.getDiscountPrice(),
            order.getUsedPoint(),
            order.getFinalPrice(),
            order.getStatus(),
            order.getCreatedAt()
        );
    }
}
