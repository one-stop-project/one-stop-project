package com.sparta.one_stop.global.outbox.service;

import com.sparta.one_stop.global.enums.outbox.OutboxEventType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import com.sparta.one_stop.global.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    // 결제 승인 Outbox 이벤트 저장
    // 결제 승인 후 Kafka 발행 전 DB에 이벤트를 먼저 기록한다
    public OutboxEvent savePaymentApprovedEvent(
        String eventId,
        Long orderId,
        String payload
    ) {
        OutboxEvent outboxEvent = OutboxEvent.paymentApproved(
            eventId,
            orderId,
            payload
        );

        return saveOutboxEvent(outboxEvent);
    }

    // 배송 완료 Outbox 이벤트 저장
    // 배송 완료 후 Kafka 발행 전 DB에 이벤트를 먼저 기록한다
    public OutboxEvent saveDeliveryCompletedEvent(
        String eventId,
        Long orderId,
        String payload
    ) {
        OutboxEvent outboxEvent = OutboxEvent.deliveryCompleted(
            eventId,
            orderId,
            payload
        );

        return saveOutboxEvent(outboxEvent);
    }

    // 범용 Outbox 이벤트 저장
    // 도메인별 이벤트 타입, 토픽, payload를 받아 Outbox 이벤트를 저장한다
    public OutboxEvent saveEvent(
        String eventId,
        OutboxEventType eventType,
        String aggregateType,
        Long aggregateId,
        String topic,
        String partitionKey,
        String payload
    ) {
        OutboxEvent outboxEvent = OutboxEvent.create(
            eventId,
            eventType,
            aggregateType,
            aggregateId,
            topic,
            partitionKey,
            payload
        );

        return saveOutboxEvent(outboxEvent);
    }

    // Outbox 이벤트 저장
    // event_id unique constraint 위반 시 OUTBOX_001로 변환한다
    private OutboxEvent saveOutboxEvent(OutboxEvent outboxEvent) {
        try {
            return outboxEventRepository.saveAndFlush(outboxEvent);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.OUTBOX_001);
        }
    }

}
