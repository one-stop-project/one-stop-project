package com.sparta.one_stop.domain.delivery.dto.response;

import com.sparta.one_stop.global.enums.OrderItemStatus;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;

public record ConfirmOrderResponse(
    Long orderItemId,
    OrderItemStatus orderItemStatus,
    DeliveryStatus deliveryStatus
) {
    public static ConfirmOrderResponse of(Long orderItemId,
                                          OrderItemStatus orderItemStatus,
                                          DeliveryStatus deliveryStatus) {
        return new ConfirmOrderResponse(orderItemId, orderItemStatus, deliveryStatus);
    }
}
