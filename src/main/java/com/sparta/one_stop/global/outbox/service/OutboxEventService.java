package com.sparta.one_stop.global.outbox.service;

import com.sparta.one_stop.global.enums.outbox.OutboxEventType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import com.sparta.one_stop.global.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    /**
     * 결제 승인 Outbox 이벤트를 별도 트랜잭션으로 저장한다.
     *
     * Outbox 저장은 결제 성공의 필수 조건이 아니므로,
     * 저장 실패가 결제 승인 트랜잭션을 rollback-only로 오염시키지 않도록
     * REQUIRES_NEW로 격리한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    /**
     * 배송 완료 Outbox 이벤트를 별도 트랜잭션으로 저장한다.
     *
     * 호출한 도메인 트랜잭션과 Outbox 저장 트랜잭션을 분리하여,
     * Outbox 저장 실패가 핵심 도메인 처리에 영향을 주지 않도록 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    /**
     * 범용 Outbox 이벤트를 별도 트랜잭션으로 저장한다.
     *
     * eventId 중복 여부는 사전 조회가 아니라 DB UNIQUE 제약을 최종 방어선으로 사용한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

    /**
     * Outbox 이벤트 저장 공통 로직.
     *
     * saveAndFlush를 사용해 event_id UNIQUE 제약 위반을 메서드 내부에서 즉시 감지하고,
     * 도메인 공통 예외 코드로 변환한다.
     *
     * 주의: private 메서드에는 트랜잭션 프록시가 적용되지 않으므로,
     * 트랜잭션 경계는 외부에서 호출되는 public 메서드에 선언한다.
     */
    private OutboxEvent saveOutboxEvent(OutboxEvent outboxEvent) {
        try {
            return outboxEventRepository.saveAndFlush(outboxEvent);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.OUTBOX_001);
        }
    }

}

