package com.sparta.one_stop.domain.delivery.dto.response;


import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;

import java.time.LocalDateTime;

public record DeliveryResponse(Long deliveryId,
                               Long orderItemId,
                               String itemName,
                               String sellerName,
                               DeliveryStatus status,
                               String invoiceNumber,
                               String deliveryCompany,
                               LocalDateTime updatedAt) {
    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
            delivery.getId(),
            delivery.getOrderItem().getId(),
            delivery.getOrderItem().getItemName(),
            delivery.getOrderItem().getSeller().getShopName(),
            delivery.getStatus(),
            delivery.getInvoiceNumber(),
            delivery.getDeliveryCompany(),
            delivery.getUpdatedAt()
        );
    }
}
