package com.sparta.one_stop.domain.order.dto.response;

import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;

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

    public static OrderDetailItemResponse of(
        OrderItem orderItem,
        Delivery delivery
    ) {
        // 결제 전 주문(PENDING_PAYMENT)은 아직 배송 정보가 생성되지 않았을 수 있음
        DeliverySummaryResponse deliveryResponse = delivery != null
            ? DeliverySummaryResponse.of(delivery)
            : null;

        return new OrderDetailItemResponse(
            orderItem.getId(),
            orderItem.getProductItem().getId(),
            orderItem.getItemName(),
            orderItem.getSeller().getId(),
            orderItem.getQuantity(),
            orderItem.getPrice(),
            orderItem.getStatus(),
            deliveryResponse
        );
    }

}
