package com.sparta.one_stop.domain.delivery.event;

import java.time.LocalDateTime;

/**
 * 배송 완료 이벤트 Payload
 * - Outbox 테이블에 JSON 형태로 저장
 * - Kafka Consumer가 역직렬화하여 포인트 적립 처리에 사용
 */
public record DeliveryCompletedEventPayload(
    String eventId,
    String eventType,
    Long orderId,
    Long userId,
    Long deliveryId,
    LocalDateTime completedAt
) {
    public static DeliveryCompletedEventPayload of(
        String eventId,
        Long orderId,
        Long userId,
        Long deliveryId,
        LocalDateTime completedAt
    ) {
        return new DeliveryCompletedEventPayload(
            eventId,
            "DELIVERY_COMPLETED",
            orderId,
            userId,
            deliveryId,
            completedAt
        );
    }
}
