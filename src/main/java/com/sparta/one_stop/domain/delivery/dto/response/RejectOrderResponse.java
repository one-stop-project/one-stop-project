package com.sparta.one_stop.domain.delivery.dto.response;

import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;

public record RejectOrderResponse(
    Long orderItemId,
    OrderItemStatus orderItemStatus,
    DeliveryStatus deliveryStatus,
    Long rejectedPrice,
    Integer restoredStock,
    boolean orderAutoCancelled
) {
    public static RejectOrderResponse of(
        Long orderItemId,
        DeliveryStatus deliveryStatus,
        Long rejectedPrice,
        Integer restoredStock,
        boolean orderAutoCancelled
    ) {
        return new RejectOrderResponse(
            orderItemId,
            OrderItemStatus.REJECTED,
            deliveryStatus,
            rejectedPrice,
            restoredStock,
            orderAutoCancelled
        );
    }
}
