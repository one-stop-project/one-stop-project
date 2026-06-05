package com.sparta.one_stop.domain.payment.event;

import com.sparta.one_stop.global.enums.outbox.OutboxEventType;

import java.time.LocalDateTime;

/**
 * 결제 승인 이벤트 Payload
 * - Outbox 테이블에 JSON 문자열로 저장되어 Kafka로 발행된다
 * - Consumer가 후속 처리(알림, 정산 등)를 수행하는 데 필요한 최소 정보를 포함한다
 */
public record PaymentApprovedEventPayload(
    String eventId,
    String eventType,
    Long orderId,
    Long paymentId,
    Long userId,
    Long finalPrice,
    LocalDateTime approvedAt
) {
    // 결제 승인 이벤트 Payload 생성 (eventType은 PAYMENT_APPROVED로 자동 설정)
    public static PaymentApprovedEventPayload of(
        String eventId,
        Long orderId,
        Long paymentId,
        Long userId,
        Long finalPrice,
        LocalDateTime approvedAt
    ) {
        return new PaymentApprovedEventPayload(
            eventId,
            OutboxEventType.PAYMENT_APPROVED.name(),
            orderId,
            paymentId,
            userId,
            finalPrice,
            approvedAt
        );
    }

}
