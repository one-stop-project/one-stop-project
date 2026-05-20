package com.sparta.one_stop.domain.delivery.dto.response;

import com.sparta.one_stop.global.enums.OrderItemStatus;

public record RejectOrderResponse(
    Long orderItemId,
    OrderItemStatus orderItemStatus,
    Long refundAmount,
    Integer restoredStock
) {
    public static RejectOrderResponse of(Long orderItemId, Long refundAmount, Integer restoredStock) {
        return new RejectOrderResponse(
            orderItemId,
            OrderItemStatus.REJECTED,
            refundAmount,
            restoredStock
        );
    }
}
