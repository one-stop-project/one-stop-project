package com.sparta.one_stop.domain.delivery.dto.response;

import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;

import java.time.LocalDateTime;

public record ShipDeliveryResponse(
    Long deliveryId,
    DeliveryStatus status,
    String deliveryCompany,
    String invoiceNumber,
    LocalDateTime shippedAt
) {
    public static ShipDeliveryResponse from(Delivery delivery) {
        return new ShipDeliveryResponse(
            delivery.getId(),
            delivery.getStatus(),
            delivery.getDeliveryCompany(),
            delivery.getInvoiceNumber(),
            delivery.getUpdatedAt()
        );
    }
}
