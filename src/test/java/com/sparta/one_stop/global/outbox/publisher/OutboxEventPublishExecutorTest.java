package com.sparta.one_stop.global.outbox.publisher;

import com.sparta.one_stop.global.alert.slack.SlackAlertService;
import com.sparta.one_stop.global.enums.outbox.OutboxEventStatus;
import com.sparta.one_stop.global.enums.outbox.OutboxEventType;
import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import com.sparta.one_stop.global.outbox.kafka.OutboxKafkaProducer;
import com.sparta.one_stop.global.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventPublishExecutorTest {

    private final OutboxEventRepository outboxEventRepository =
        mock(OutboxEventRepository.class);

    private final OutboxKafkaProducer outboxKafkaProducer =
        mock(OutboxKafkaProducer.class);

    private final SlackAlertService slackAlertService =
        mock(SlackAlertService.class);

    private final OutboxEventPublishExecutor outboxEventPublishExecutor =
        new OutboxEventPublishExecutor(
            outboxEventRepository,
            outboxKafkaProducer,
            slackAlertService
        );

    @Test
    @DisplayName("execute 성공 - 선점 후 Kafka 발행에 성공하면 PUBLISHED 상태로 저장한다")
    void execute_success() {
        // given
        OutboxEvent pendingEvent = pendingOutboxEvent(1L);
        OutboxEvent processingEvent = processingOutboxEvent(1L);

        when(outboxEventRepository.updateStatusByIdAndStatus(
            1L,
            OutboxEventStatus.PENDING,
            OutboxEventStatus.PROCESSING
        )).thenReturn(1);

        when(outboxEventRepository.findById(1L))
            .thenReturn(Optional.of(processingEvent));

        // when
        outboxEventPublishExecutor.execute(pendingEvent);

        // then
        verify(outboxKafkaProducer).send(
            "payment.approved",
            "1",
            "{\"orderId\":1}"
        );

        ArgumentCaptor<OutboxEvent> captor =
            ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent savedEvent = captor.getValue();

        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(savedEvent.getProcessedAt()).isNotNull();
        assertThat(savedEvent.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("execute 스킵 - 선점 실패하면 Kafka 발행과 저장을 하지 않는다")
    void execute_skip_whenAlreadyClaimed() {
        // given
        OutboxEvent pendingEvent = pendingOutboxEvent(1L);

        when(outboxEventRepository.updateStatusByIdAndStatus(
            1L,
            OutboxEventStatus.PENDING,
            OutboxEventStatus.PROCESSING
        )).thenReturn(0);

        // when
        outboxEventPublishExecutor.execute(pendingEvent);

        // then
        verify(outboxEventRepository, never()).findById(1L);
        verify(outboxKafkaProducer, never()).send(
            any(),
            any(),
            any()
        );
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("execute 실패 - Kafka 발행 실패가 재시도 한도 이내이면 PENDING 상태로 복구한다")
    void execute_fail_whenKafkaSendFailsWithinMaxRetryCount() {
        // given
        OutboxEvent pendingEvent = pendingOutboxEvent(1L);
        OutboxEvent processingEvent = processingOutboxEvent(1L);

        when(outboxEventRepository.updateStatusByIdAndStatus(
            1L,
            OutboxEventStatus.PENDING,
            OutboxEventStatus.PROCESSING
        )).thenReturn(1);

        when(outboxEventRepository.findById(1L))
            .thenReturn(Optional.of(processingEvent));

        doThrow(new RuntimeException("kafka error"))
            .when(outboxKafkaProducer)
            .send(
                processingEvent.getTopic(),
                processingEvent.getPartitionKey(),
                processingEvent.getPayload()
            );

        // when
        outboxEventPublishExecutor.execute(pendingEvent);

        // then
        ArgumentCaptor<OutboxEvent> captor =
            ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent savedEvent = captor.getValue();

        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(savedEvent.getRetryCount()).isEqualTo(1);
        assertThat(savedEvent.getLastErrorMessage()).contains("kafka error");
        assertThat(savedEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("execute 실패 - Kafka 발행 실패가 재시도 한도를 초과하면 DEAD 상태로 저장한다")
    void execute_fail_whenKafkaSendFailsOverMaxRetryCount() {
        // given
        OutboxEvent pendingEvent = pendingOutboxEvent(1L);
        OutboxEvent processingEvent = processingOutboxEvent(1L);
        setField(processingEvent, "retryCount", 3);

        when(outboxEventRepository.updateStatusByIdAndStatus(
            1L,
            OutboxEventStatus.PENDING,
            OutboxEventStatus.PROCESSING
        )).thenReturn(1);

        when(outboxEventRepository.findById(1L))
            .thenReturn(Optional.of(processingEvent));

        doThrow(new RuntimeException("kafka error"))
            .when(outboxKafkaProducer)
            .send(
                processingEvent.getTopic(),
                processingEvent.getPartitionKey(),
                processingEvent.getPayload()
            );

        // when
        outboxEventPublishExecutor.execute(pendingEvent);

        // then
        ArgumentCaptor<OutboxEvent> captor =
            ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(captor.capture());
        verify(slackAlertService).sendOutboxDeadAlert(any(OutboxEvent.class));

        OutboxEvent savedEvent = captor.getValue();

        assertThat(savedEvent.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(savedEvent.getRetryCount()).isEqualTo(4);
        assertThat(savedEvent.getLastErrorMessage()).contains("kafka error");
        assertThat(savedEvent.getProcessedAt()).isNotNull();
    }

    private OutboxEvent pendingOutboxEvent(Long id) {
        OutboxEvent outboxEvent = OutboxEvent.create(
            "event-" + id,
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            "payment.approved",
            "1",
            "{\"orderId\":1}"
        );

        setField(outboxEvent, "id", id);

        return outboxEvent;
    }

    private OutboxEvent processingOutboxEvent(Long id) {
        OutboxEvent outboxEvent = pendingOutboxEvent(id);

        outboxEvent.markProcessing();

        return outboxEvent;
    }

    private void setField(
        Object target,
        String fieldName,
        Object value
    ) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
