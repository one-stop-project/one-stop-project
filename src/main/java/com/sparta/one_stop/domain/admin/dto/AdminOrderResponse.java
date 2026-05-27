package com.sparta.one_stop.domain.admin.dto;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.global.enums.order.OrderStatus;

import java.time.LocalDateTime;

public record AdminOrderResponse(
    Long orderId,
    // 구매자 이름
    String userName,
    // 구매자 이메일
    String userEmail,
    // 최종 결제 금액
    Long finalPrice,
    // 주문 상태
    OrderStatus status,
    // 주문 상품 종류 수
    int itemCount,
    LocalDateTime createdAt
) {
    public static AdminOrderResponse from(Order order, int itemCount) {
        return new AdminOrderResponse(
            order.getId(),
            order.getUser().getName(),
            order.getUser().getEmail(),
            order.getFinalPrice(),
            order.getStatus(),
            itemCount,
            order.getCreatedAt()
        );
    }
}
