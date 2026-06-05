package com.sparta.one_stop.global.outbox.service;

import com.sparta.one_stop.global.enums.outbox.OutboxEventType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import com.sparta.one_stop.global.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
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
        validateDuplicateEvent(eventId);

        OutboxEvent outboxEvent = OutboxEvent.paymentApproved(
            eventId,
            orderId,
            payload
        );

        return outboxEventRepository.save(outboxEvent);
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
        validateDuplicateEvent(eventId);

        OutboxEvent outboxEvent = OutboxEvent.create(
            eventId,
            eventType,
            aggregateType,
            aggregateId,
            topic,
            partitionKey,
            payload
        );

        return outboxEventRepository.save(outboxEvent);
    }

    // 이벤트 고유 ID 기준 중복 여부 확인
    // 이미 저장된 이벤트이면 멱등성 보장을 위해 예외를 발생시킨다
    private void validateDuplicateEvent(String eventId) {
        if (outboxEventRepository.findByEventId(eventId).isPresent()) {
            throw new CustomException(ErrorCode.OUTBOX_001);
        }
    }

}
