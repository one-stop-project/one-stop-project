package com.sparta.one_stop.domain.seller.dto.response;

import com.sparta.one_stop.global.enums.order.OrderItemStatus;

import java.util.EnumMap;
import java.util.Map;

public record SellerOrderStatusCountResponse(
    long ordered,
    long confirmed,
    long shipping,
    long delivered,
    long cancelled,
    long rejected,
    long total
) {
    public static SellerOrderStatusCountResponse from(Map<OrderItemStatus, Long> counts) {
        Map<OrderItemStatus, Long> safe = new EnumMap<>(OrderItemStatus.class);
        if (counts != null) safe.putAll(counts);
        long ordered = safe.getOrDefault(OrderItemStatus.ORDERED, 0L);
        long confirmed = safe.getOrDefault(OrderItemStatus.CONFIRMED, 0L);
        long shipping = safe.getOrDefault(OrderItemStatus.SHIPPING, 0L);
        long delivered = safe.getOrDefault(OrderItemStatus.DELIVERED, 0L);
        long cancelled = safe.getOrDefault(OrderItemStatus.CANCELLED, 0L);
        long rejected = safe.getOrDefault(OrderItemStatus.REJECTED, 0L);
        return new SellerOrderStatusCountResponse(
            ordered, confirmed, shipping, delivered, cancelled, rejected,
            ordered + confirmed + shipping + delivered + cancelled + rejected
        );
    }
}
