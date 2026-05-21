package com.sparta.one_stop.domain.delivery.dto.response;

import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;

import java.time.LocalDateTime;

public record UpdateDeliveryStatusResponse(
    Long deliveryId,
    DeliveryStatus status,
    LocalDateTime updatedAt
) {
    public static UpdateDeliveryStatusResponse from(Delivery delivery) {
        return new UpdateDeliveryStatusResponse(
            delivery.getId(),
            delivery.getStatus(),
            delivery.getUpdatedAt()
        );
    }
}
