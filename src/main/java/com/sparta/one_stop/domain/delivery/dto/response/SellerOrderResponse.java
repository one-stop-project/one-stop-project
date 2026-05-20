package com.sparta.one_stop.domain.delivery.dto.response;

import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.global.enums.OrderItemStatus;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;

import java.time.LocalDateTime;

public record SellerOrderResponse(
    Long orderItemId,
    Long orderId,
    String itemName,
    Integer quantity,
    Long price,
    Long subtotal,
    String buyerName,
    String receiverAddress,
    OrderItemStatus orderItemStatus,
    DeliveryStatus deliveryStatus,
    LocalDateTime orderedAt
) {
    public static SellerOrderResponse from(OrderItem orderItem, Delivery delivery) {
        return new SellerOrderResponse(
            orderItem.getId(),
            orderItem.getOrder().getId(),
            orderItem.getItemName(),
            orderItem.getQuantity(),
            orderItem.getPrice(),
            orderItem.getPrice() * orderItem.getQuantity(),
            maskName(orderItem.getOrder().getReceiverName()),
            orderItem.getOrder().getReceiverAddress(),
            orderItem.getStatus(),
            delivery.getStatus(),
            orderItem.getCreatedAt()
        );
    }

    private static String maskName(String name) {
        if (name == null || name.length() <= 1) return name;
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}

