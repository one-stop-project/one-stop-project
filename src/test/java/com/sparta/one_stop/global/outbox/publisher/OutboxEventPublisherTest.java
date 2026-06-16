package com.sparta.one_stop.global.outbox.publisher;

import com.sparta.one_stop.global.enums.outbox.OutboxEventStatus;
import com.sparta.one_stop.global.enums.outbox.OutboxEventType;
import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import com.sparta.one_stop.global.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventPublisherTest {

    private final OutboxEventRepository outboxEventRepository =
        mock(OutboxEventRepository.class);

    private final OutboxEventPublishExecutor outboxEventPublishExecutor =
        mock(OutboxEventPublishExecutor.class);

    private final OutboxEventPublisher outboxEventPublisher =
        new OutboxEventPublisher(
            outboxEventRepository,
            outboxEventPublishExecutor
        );

    @Test
    @DisplayName("publishPendingEvents 성공 - PENDING 이벤트가 있으면 Executor.execute를 호출한다")
    void publishPendingEvents_success_whenPendingEventsExist() {
        // given
        OutboxEvent event1 = pendingOutboxEvent(1L);
        OutboxEvent event2 = pendingOutboxEvent(2L);

        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(
            eq(OutboxEventStatus.PENDING),
            any(Pageable.class)
        )).thenReturn(List.of(event1, event2));

        // when
        outboxEventPublisher.publishPendingEvents();

        // then
        verify(outboxEventPublishExecutor).execute(event1);
        verify(outboxEventPublishExecutor).execute(event2);
    }

    @Test
    @DisplayName("publishPendingEvents 성공 - PENDING 이벤트가 없으면 Executor를 호출하지 않는다")
    void publishPendingEvents_success_whenPendingEventsEmpty() {
        // given
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(
            eq(OutboxEventStatus.PENDING),
            any(Pageable.class)
        )).thenReturn(List.of());

        // when
        outboxEventPublisher.publishPendingEvents();

        // then
        verify(outboxEventPublishExecutor, never()).execute(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("publishPendingEvents 성공 - 단일 이벤트 처리 실패 시에도 이후 이벤트 처리를 계속한다")
    void publishPendingEvents_success_whenSingleEventFails() {
        // given
        OutboxEvent event1 = pendingOutboxEvent(1L);
        OutboxEvent event2 = pendingOutboxEvent(2L);
        OutboxEvent event3 = pendingOutboxEvent(3L);

        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(
            eq(OutboxEventStatus.PENDING),
            any(Pageable.class)
        )).thenReturn(List.of(event1, event2, event3));

        doThrow(new RuntimeException("테스트용 Outbox 이벤트 처리 실패"))
            .when(outboxEventPublishExecutor)
            .execute(event1);

        // when & then
        assertDoesNotThrow(outboxEventPublisher::publishPendingEvents);

        verify(outboxEventPublishExecutor).execute(event1);
        verify(outboxEventPublishExecutor).execute(event2);
        verify(outboxEventPublishExecutor).execute(event3);
    }

    private OutboxEvent pendingOutboxEvent(Long id) {
        OutboxEvent outboxEvent = OutboxEvent.create(
            "event-" + id,
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            id,
            "payment.approved",
            String.valueOf(id),
            "{\"orderId\":" + id + "}"
        );

        setField(outboxEvent, "id", id);

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
