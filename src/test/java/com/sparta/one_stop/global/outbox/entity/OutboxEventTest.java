package com.sparta.one_stop.global.outbox.entity;

import com.sparta.one_stop.global.enums.outbox.OutboxEventStatus;
import com.sparta.one_stop.global.enums.outbox.OutboxEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventTest {

    @Test
    @DisplayName("paymentApproved 생성 성공 - 결제 승인 이벤트를 생성하면 PENDING 상태와 retryCount 0으로 초기화된다")
    void paymentApproved_success() {
        // when
        OutboxEvent outboxEvent = OutboxEvent.paymentApproved(
            "event-1",
            1L,
            "{\"orderId\":1}"
        );

        // then
        assertThat(outboxEvent.getEventId()).isEqualTo("event-1");
        assertThat(outboxEvent.getEventType()).isEqualTo(OutboxEventType.PAYMENT_APPROVED);
        assertThat(outboxEvent.getAggregateType()).isEqualTo("ORDER");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(1L);
        assertThat(outboxEvent.getTopic()).isEqualTo("payment.approved");
        assertThat(outboxEvent.getPartitionKey()).isEqualTo("1");
        assertThat(outboxEvent.getPayload()).isEqualTo("{\"orderId\":1}");
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(0);
        assertThat(outboxEvent.getLastErrorMessage()).isNull();
        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("create 성공 - 범용 정적 팩토리로 OutboxEvent를 생성하면 모든 필드가 인자대로 설정된다")
    void create_success() {
        // when
        OutboxEvent outboxEvent = OutboxEvent.create(
            "event-1",
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            "payment.approved",
            "1",
            "{\"orderId\":1}"
        );

        // then
        assertThat(outboxEvent.getEventId()).isEqualTo("event-1");
        assertThat(outboxEvent.getEventType()).isEqualTo(OutboxEventType.PAYMENT_APPROVED);
        assertThat(outboxEvent.getAggregateType()).isEqualTo("ORDER");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(1L);
        assertThat(outboxEvent.getTopic()).isEqualTo("payment.approved");
        assertThat(outboxEvent.getPartitionKey()).isEqualTo("1");
        assertThat(outboxEvent.getPayload()).isEqualTo("{\"orderId\":1}");
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(0);
        assertThat(outboxEvent.getLastErrorMessage()).isNull();
        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - eventId가 null이면 예외가 발생한다")
    void create_fail_whenEventIdIsNull() {
        // when & then
        assertThatThrownBy(() -> createOutboxEvent(
            null,
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            "payment.approved",
            "1",
            "{\"orderId\":1}"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - eventId가 공백이면 예외가 발생한다")
    void create_fail_whenEventIdIsBlank() {
        // when & then
        assertThatThrownBy(() -> createOutboxEvent(
            " ",
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            "payment.approved",
            "1",
            "{\"orderId\":1}"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - eventType이 null이면 예외가 발생한다")
    void create_fail_whenEventTypeIsNull() {
        // when & then
        assertThatThrownBy(() -> createOutboxEvent(
            "event-1",
            null,
            "ORDER",
            1L,
            "payment.approved",
            "1",
            "{\"orderId\":1}"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - aggregateType이 null이면 예외가 발생한다")
    void create_fail_whenAggregateTypeIsNull() {
        // when & then
        assertThatThrownBy(() -> createOutboxEvent(
            "event-1",
            OutboxEventType.PAYMENT_APPROVED,
            null,
            1L,
            "payment.approved",
            "1",
            "{\"orderId\":1}"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - aggregateType이 공백이면 예외가 발생한다")
    void create_fail_whenAggregateTypeIsBlank() {
        // when & then
        assertThatThrownBy(() -> createOutboxEvent(
            "event-1",
            OutboxEventType.PAYMENT_APPROVED,
            " ",
            1L,
            "payment.approved",
            "1",
            "{\"orderId\":1}"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - aggregateId가 null이면 예외가 발생한다")
    void create_fail_whenAggregateIdIsNull() {
        // when & then
        assertThatThrownBy(() -> createOutboxEvent(
            "event-1",
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            null,
            "payment.approved",
            "1",
            "{\"orderId\":1}"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - topic이 null이면 예외가 발생한다")
    void create_fail_whenTopicIsNull() {
        // when & then
        assertThatThrownBy(() -> createOutboxEvent(
            "event-1",
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            null,
            "1",
            "{\"orderId\":1}"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - topic이 공백이면 예외가 발생한다")
    void create_fail_whenTopicIsBlank() {
        // when & then
        assertThatThrownBy(() -> createOutboxEvent(
            "event-1",
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            " ",
            "1",
            "{\"orderId\":1}"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - partitionKey가 null이면 예외가 발생한다")
    void create_fail_whenPartitionKeyIsNull() {
        // when & then
        assertThatThrownBy(() -> createOutboxEvent(
            "event-1",
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            "payment.approved",
            null,
            "{\"orderId\":1}"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - partitionKey가 공백이면 예외가 발생한다")
    void create_fail_whenPartitionKeyIsBlank() {
        // when & then
        assertThatThrownBy(() -> createOutboxEvent(
            "event-1",
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            "payment.approved",
            " ",
            "{\"orderId\":1}"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - payload가 null이면 예외가 발생한다")
    void create_fail_whenPayloadIsNull() {
        // when & then
        assertThatThrownBy(() -> createOutboxEvent(
            "event-1",
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            "payment.approved",
            "1",
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("OutboxEvent 생성 실패 - payload가 공백이면 예외가 발생한다")
    void create_fail_whenPayloadIsBlank() {
        // when & then
        assertThatThrownBy(() -> createOutboxEvent(
            "event-1",
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            "payment.approved",
            "1",
            " "
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("markProcessing 성공 - PENDING 상태의 이벤트를 PROCESSING 상태로 변경한다")
    void markProcessing_success() {
        // given
        OutboxEvent outboxEvent = pendingOutboxEvent();

        // when
        outboxEvent.markProcessing();

        // then
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("markProcessing 성공 - markFailure로 PENDING이 된 후 markProcessing 호출 시 processedAt이 null로 유지된다")
    void markProcessing_success_afterMarkFailure() {
        // given - markFailure(retry 한도 이내)로 PENDING 복귀
        OutboxEvent outboxEvent = processingOutboxEvent();
        outboxEvent.markFailure("temporary error", 3);  // PROCESSING → PENDING

        // when - 다시 PROCESSING으로 선점
        outboxEvent.markProcessing();

        // then
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("markProcessing 실패 - PROCESSING 상태이면 예외가 발생한다")
    void markProcessing_fail_whenStatusIsProcessing() {
        // given
        OutboxEvent outboxEvent = processingOutboxEvent();

        // when & then
        assertThatThrownBy(outboxEvent::markProcessing)
            .isInstanceOf(IllegalStateException.class);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
    }

    @Test
    @DisplayName("markProcessing 실패 - PUBLISHED 상태이면 예외가 발생한다")
    void markProcessing_fail_whenStatusIsPublished() {
        // given
        OutboxEvent outboxEvent = publishedOutboxEvent();
        LocalDateTime processedAt = outboxEvent.getProcessedAt();

        // when & then
        assertThatThrownBy(outboxEvent::markProcessing)
            .isInstanceOf(IllegalStateException.class);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEvent.getProcessedAt()).isEqualTo(processedAt);
    }

    @Test
    @DisplayName("markProcessing 실패 - DEAD 상태이면 예외가 발생한다")
    void markProcessing_fail_whenStatusIsDead() {
        // given
        OutboxEvent outboxEvent = deadOutboxEvent();
        LocalDateTime processedAt = outboxEvent.getProcessedAt();

        // when & then
        assertThatThrownBy(outboxEvent::markProcessing)
            .isInstanceOf(IllegalStateException.class);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(outboxEvent.getProcessedAt()).isEqualTo(processedAt);
    }

    @Test
    @DisplayName("markPublished 성공 - PROCESSING 상태의 이벤트를 PUBLISHED 상태로 변경한다")
    void markPublished_success() {
        // given
        OutboxEvent outboxEvent = processingOutboxEvent();

        // when
        outboxEvent.markPublished();

        // then
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEvent.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("markPublished 실패 - PENDING 상태이면 예외가 발생한다")
    void markPublished_fail_whenStatusIsPending() {
        // given
        OutboxEvent outboxEvent = pendingOutboxEvent();

        // when & then
        assertThatThrownBy(outboxEvent::markPublished)
            .isInstanceOf(IllegalStateException.class);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("markPublished 실패 - PUBLISHED 상태이면 예외가 발생한다")
    void markPublished_fail_whenStatusIsPublished() {
        // given
        OutboxEvent outboxEvent = publishedOutboxEvent();
        LocalDateTime processedAt = outboxEvent.getProcessedAt();

        // when & then
        assertThatThrownBy(outboxEvent::markPublished)
            .isInstanceOf(IllegalStateException.class);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEvent.getProcessedAt()).isEqualTo(processedAt);
    }

    @Test
    @DisplayName("markPublished 실패 - DEAD 상태이면 예외가 발생한다")
    void markPublished_fail_whenStatusIsDead() {
        // given
        OutboxEvent outboxEvent = deadOutboxEvent();
        LocalDateTime processedAt = outboxEvent.getProcessedAt();

        // when & then
        assertThatThrownBy(outboxEvent::markPublished)
            .isInstanceOf(IllegalStateException.class);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(outboxEvent.getProcessedAt()).isEqualTo(processedAt);
    }

    @Test
    @DisplayName("markFailure 성공 - 최대 재시도 횟수 이내이면 PENDING 상태로 변경한다")
    void markFailure_success_whenWithinMaxRetryCount() {
        // given
        OutboxEvent outboxEvent = processingOutboxEvent();

        // when
        outboxEvent.markFailure("kafka error", 3);

        // then
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getLastErrorMessage()).isEqualTo("kafka error");
        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("markFailure 성공 - 재시도 횟수가 최대 재시도 횟수와 같으면 PENDING 상태로 변경한다")
    void markFailure_success_whenReachingMaxRetryCount() {
        // given
        OutboxEvent outboxEvent = processingOutboxEvent();

        // when
        outboxEvent.markFailure("kafka error", 1);

        // then
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getLastErrorMessage()).isEqualTo("kafka error");
        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("markFailure 성공 - 재시도 횟수가 최대 재시도 횟수를 초과하면 DEAD 상태로 변경한다")
    void markFailure_success_whenExceedingMaxRetryCount() {
        // given
        OutboxEvent outboxEvent = processingOutboxEvent();

        // when
        outboxEvent.markFailure("kafka error", 0);

        // then
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getLastErrorMessage()).isEqualTo("kafka error");
        assertThat(outboxEvent.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailure 성공 - 에러 메시지가 null이면 null로 저장한다")
    void markFailure_success_whenErrorMessageIsNull() {
        // given
        OutboxEvent outboxEvent = processingOutboxEvent();

        // when
        outboxEvent.markFailure(null, 3);

        // then
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getLastErrorMessage()).isNull();
        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("markFailure 성공 - 에러 메시지가 정확히 500자이면 그대로 저장한다")
    void markFailure_success_whenErrorMessageIsExactly500() {
        // given
        OutboxEvent outboxEvent = processingOutboxEvent();
        String errorMessage = "a".repeat(500);

        // when
        outboxEvent.markFailure(errorMessage, 3);

        // then
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getLastErrorMessage()).isEqualTo(errorMessage);
        assertThat(outboxEvent.getLastErrorMessage()).hasSize(500);
        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("markFailure 성공 - 에러 메시지가 500자를 초과하면 500자로 자른다")
    void markFailure_success_whenErrorMessageOver500() {
        // given
        OutboxEvent outboxEvent = processingOutboxEvent();
        String errorMessage = "a".repeat(501);

        // when
        outboxEvent.markFailure(errorMessage, 3);

        // then
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getLastErrorMessage()).isEqualTo("a".repeat(500));
        assertThat(outboxEvent.getLastErrorMessage()).hasSize(500);
        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("markFailure 실패 - 최대 재시도 횟수가 음수이면 예외가 발생한다")
    void markFailure_fail_whenMaxRetryCountIsNegative() {
        // given
        OutboxEvent outboxEvent = processingOutboxEvent();

        // when & then
        assertThatThrownBy(() -> outboxEvent.markFailure("kafka error", -1))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(0);
        assertThat(outboxEvent.getLastErrorMessage()).isNull();
        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("markFailure 실패 - PENDING 상태이면 예외가 발생한다")
    void markFailure_fail_whenStatusIsPending() {
        // given
        OutboxEvent outboxEvent = pendingOutboxEvent();

        // when & then
        assertThatThrownBy(() -> outboxEvent.markFailure("kafka error", 3))
            .isInstanceOf(IllegalStateException.class);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(0);
        assertThat(outboxEvent.getLastErrorMessage()).isNull();
        assertThat(outboxEvent.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("markFailure 실패 - PUBLISHED 상태이면 예외가 발생한다")
    void markFailure_fail_whenStatusIsPublished() {
        // given
        OutboxEvent outboxEvent = publishedOutboxEvent();
        LocalDateTime processedAt = outboxEvent.getProcessedAt();

        // when & then
        assertThatThrownBy(() -> outboxEvent.markFailure("kafka error", 3))
            .isInstanceOf(IllegalStateException.class);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(0);
        assertThat(outboxEvent.getLastErrorMessage()).isNull();
        assertThat(outboxEvent.getProcessedAt()).isEqualTo(processedAt);
    }

    @Test
    @DisplayName("markFailure 실패 - DEAD 상태이면 예외가 발생한다")
    void markFailure_fail_whenStatusIsDead() {
        // given
        OutboxEvent outboxEvent = deadOutboxEvent();
        LocalDateTime processedAt = outboxEvent.getProcessedAt();

        // when & then
        assertThatThrownBy(() -> outboxEvent.markFailure("kafka error", 3))
            .isInstanceOf(IllegalStateException.class);

        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.DEAD);
        assertThat(outboxEvent.getRetryCount()).isEqualTo(1);
        assertThat(outboxEvent.getLastErrorMessage()).isEqualTo("kafka error");
        assertThat(outboxEvent.getProcessedAt()).isEqualTo(processedAt);
    }

    private OutboxEvent createOutboxEvent(
        String eventId,
        OutboxEventType eventType,
        String aggregateType,
        Long aggregateId,
        String topic,
        String partitionKey,
        String payload
    ) {
        return OutboxEvent.create(
            eventId,
            eventType,
            aggregateType,
            aggregateId,
            topic,
            partitionKey,
            payload
        );
    }

    private OutboxEvent pendingOutboxEvent() {
        return OutboxEvent.create(
            "event-1",
            OutboxEventType.PAYMENT_APPROVED,
            "ORDER",
            1L,
            "payment.approved",
            "1",
            "{\"orderId\":1}"
        );
    }

    private OutboxEvent processingOutboxEvent() {
        OutboxEvent outboxEvent = pendingOutboxEvent();

        outboxEvent.markProcessing();

        return outboxEvent;
    }

    private OutboxEvent publishedOutboxEvent() {
        OutboxEvent outboxEvent = processingOutboxEvent();

        outboxEvent.markPublished();

        return outboxEvent;
    }

    private OutboxEvent deadOutboxEvent() {
        OutboxEvent outboxEvent = processingOutboxEvent();

        outboxEvent.markFailure("kafka error", 0);

        return outboxEvent;
    }

}
