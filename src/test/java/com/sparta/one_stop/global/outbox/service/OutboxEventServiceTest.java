package com.sparta.one_stop.global.outbox.service;

import com.sparta.one_stop.global.enums.outbox.OutboxEventStatus;
import com.sparta.one_stop.global.enums.outbox.OutboxEventType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import com.sparta.one_stop.global.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxEventService outboxEventService;

    @Test
    @DisplayName("savePaymentApprovedEvent 성공 - 중복 이벤트가 없으면 결제 승인 Outbox 이벤트를 저장한다")
    void savePaymentApprovedEvent_success() {
        // given
        String eventId = "event-1";
        Long orderId = 1L;
        String payload = "{\"orderId\":1}";

        when(outboxEventRepository.findByEventId(eventId))
            .thenReturn(Optional.empty());

        when(outboxEventRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        OutboxEvent savedEvent = outboxEventService.savePaymentApprovedEvent(
            eventId,
            orderId,
            payload
        );

        // then
        assertThat(savedEvent.getEventId()).isEqualTo(eventId);
        assertThat(savedEvent.getEventType()).isEqualTo(OutboxEventType.PAYMENT_APPROVED);
        assertThat(savedEvent.getAggregateType()).isEqualTo("ORDER");
        assertThat(savedEvent.getAggregateId()).isEqualTo(orderId);
        assertThat(savedEvent.getTopic()).isEqualTo("payment.approved");
        assertThat(savedEvent.getPartitionKey()).isEqualTo(String.valueOf(orderId));
        assertThat(savedEvent.getPayload()).isEqualTo(payload);
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedEvent.getRetryCount()).isEqualTo(0);
        assertThat(savedEvent.getLastErrorMessage()).isNull();
        assertThat(savedEvent.getProcessedAt()).isNull();

        verify(outboxEventRepository).findByEventId(eventId);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("savePaymentApprovedEvent 성공 - Repository에 저장되는 OutboxEvent 필드를 검증한다")
    void savePaymentApprovedEvent_success_verifySavedEntity() {
        // given
        String eventId = "event-1";
        Long orderId = 1L;
        String payload = "{\"orderId\":1}";

        when(outboxEventRepository.findByEventId(eventId))
            .thenReturn(Optional.empty());

        when(outboxEventRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        outboxEventService.savePaymentApprovedEvent(
            eventId,
            orderId,
            payload
        );

        // then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent outboxEvent = captor.getValue();

        assertThat(outboxEvent.getEventId()).isEqualTo(eventId);
        assertThat(outboxEvent.getEventType()).isEqualTo(OutboxEventType.PAYMENT_APPROVED);
        assertThat(outboxEvent.getAggregateType()).isEqualTo("ORDER");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(orderId);
        assertThat(outboxEvent.getTopic()).isEqualTo("payment.approved");
        assertThat(outboxEvent.getPartitionKey()).isEqualTo(String.valueOf(orderId));
        assertThat(outboxEvent.getPayload()).isEqualTo(payload);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("savePaymentApprovedEvent 실패 - 이미 같은 eventId가 존재하면 예외가 발생한다")
    void savePaymentApprovedEvent_fail_whenEventIdAlreadyExists() {
        // given
        String eventId = "event-1";
        Long orderId = 1L;
        String payload = "{\"orderId\":1}";

        OutboxEvent existingEvent = OutboxEvent.paymentApproved(
            eventId,
            orderId,
            payload
        );

        when(outboxEventRepository.findByEventId(eventId))
            .thenReturn(Optional.of(existingEvent));

        // when & then
        assertThatThrownBy(() -> outboxEventService.savePaymentApprovedEvent(
            eventId,
            orderId,
            payload
        ))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUTBOX_001);

        verify(outboxEventRepository).findByEventId(eventId);
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("saveEvent 성공 - 중복 이벤트가 없으면 범용 Outbox 이벤트를 저장한다")
    void saveEvent_success() {
        // given
        String eventId = "event-1";
        OutboxEventType eventType = OutboxEventType.PAYMENT_APPROVED;
        String aggregateType = "ORDER";
        Long aggregateId = 1L;
        String topic = "payment.approved";
        String partitionKey = "1";
        String payload = "{\"orderId\":1}";

        when(outboxEventRepository.findByEventId(eventId))
            .thenReturn(Optional.empty());

        when(outboxEventRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        OutboxEvent savedEvent = outboxEventService.saveEvent(
            eventId,
            eventType,
            aggregateType,
            aggregateId,
            topic,
            partitionKey,
            payload
        );

        // then
        assertThat(savedEvent.getEventId()).isEqualTo(eventId);
        assertThat(savedEvent.getEventType()).isEqualTo(eventType);
        assertThat(savedEvent.getAggregateType()).isEqualTo(aggregateType);
        assertThat(savedEvent.getAggregateId()).isEqualTo(aggregateId);
        assertThat(savedEvent.getTopic()).isEqualTo(topic);
        assertThat(savedEvent.getPartitionKey()).isEqualTo(partitionKey);
        assertThat(savedEvent.getPayload()).isEqualTo(payload);
        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedEvent.getRetryCount()).isEqualTo(0);
        assertThat(savedEvent.getLastErrorMessage()).isNull();
        assertThat(savedEvent.getProcessedAt()).isNull();

        verify(outboxEventRepository).findByEventId(eventId);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("saveEvent 성공 - Repository에 저장되는 OutboxEvent 필드를 검증한다")
    void saveEvent_success_verifySavedEntity() {
        // given
        String eventId = "event-1";
        OutboxEventType eventType = OutboxEventType.PAYMENT_APPROVED;
        String aggregateType = "ORDER";
        Long aggregateId = 1L;
        String topic = "payment.approved";
        String partitionKey = "1";
        String payload = "{\"orderId\":1}";

        when(outboxEventRepository.findByEventId(eventId))
            .thenReturn(Optional.empty());

        when(outboxEventRepository.save(any(OutboxEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        outboxEventService.saveEvent(
            eventId,
            eventType,
            aggregateType,
            aggregateId,
            topic,
            partitionKey,
            payload
        );

        // then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent outboxEvent = captor.getValue();

        assertThat(outboxEvent.getEventId()).isEqualTo(eventId);
        assertThat(outboxEvent.getEventType()).isEqualTo(eventType);
        assertThat(outboxEvent.getAggregateType()).isEqualTo(aggregateType);
        assertThat(outboxEvent.getAggregateId()).isEqualTo(aggregateId);
        assertThat(outboxEvent.getTopic()).isEqualTo(topic);
        assertThat(outboxEvent.getPartitionKey()).isEqualTo(partitionKey);
        assertThat(outboxEvent.getPayload()).isEqualTo(payload);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("saveEvent 실패 - 이미 같은 eventId가 존재하면 예외가 발생한다")
    void saveEvent_fail_whenEventIdAlreadyExists() {
        // given
        String eventId = "event-1";

        OutboxEvent existingEvent = OutboxEvent.create(
            eventId,
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            "payment.approved",
            "1",
            "{\"orderId\":1}"
        );

        when(outboxEventRepository.findByEventId(eventId))
            .thenReturn(Optional.of(existingEvent));

        // when & then
        assertThatThrownBy(() -> outboxEventService.saveEvent(
            eventId,
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            "payment.approved",
            "1",
            "{\"orderId\":1}"
        ))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUTBOX_001);

        verify(outboxEventRepository).findByEventId(eventId);
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

}
