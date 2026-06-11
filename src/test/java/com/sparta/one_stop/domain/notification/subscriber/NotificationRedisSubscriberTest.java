package com.sparta.one_stop.domain.notification.subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.one_stop.domain.notification.dto.pubsub.NotificationPubSubMessage;
import com.sparta.one_stop.domain.notification.dto.response.NotificationSseResponse;
import com.sparta.one_stop.global.enums.notification.NotificationType;
import com.sparta.one_stop.global.sse.SseConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificationRedisSubscriberTest {

    private ObjectMapper objectMapper;
    private SseConnectionManager sseConnectionManager;
    private NotificationRedisSubscriber notificationRedisSubscriber;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sseConnectionManager = mock(SseConnectionManager.class);

        notificationRedisSubscriber = new NotificationRedisSubscriber(
            objectMapper,
            sseConnectionManager
        );
    }

    @Test
    @DisplayName("onMessage 성공 - Redis 메시지를 SSE 응답으로 변환해 전송한다")
    void onMessage_success() throws Exception {
        // given
        NotificationPubSubMessage message = new NotificationPubSubMessage(
            999L,
            3L,
            "manual-redis-pubsub-test-1",
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            "Redis Pub/Sub 다중 서버 알림 테스트입니다.",
            LocalDateTime.of(2026, 6, 11, 20, 20)
        );

        String payload = objectMapper.writeValueAsString(message);

        // when
        notificationRedisSubscriber.onMessage(payload);

        // then
        ArgumentCaptor<NotificationSseResponse> responseCaptor =
            ArgumentCaptor.forClass(NotificationSseResponse.class);

        verify(sseConnectionManager).send(
            eq(3L),
            responseCaptor.capture()
        );

        NotificationSseResponse response = responseCaptor.getValue();

        assertThat(response.notificationId()).isEqualTo(999L);
        assertThat(response.type()).isEqualTo(NotificationType.PAYMENT_APPROVED);
        assertThat(response.title()).isEqualTo("결제 완료");
        assertThat(response.message()).isEqualTo("Redis Pub/Sub 다중 서버 알림 테스트입니다.");
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 6, 11, 20, 20));
    }

    @Test
    @DisplayName("onMessage 스킵 - 잘못된 JSON 메시지는 SSE 전송하지 않는다")
    void onMessage_skip_whenInvalidJson() {
        // given
        String invalidPayload = "{ invalid-json ";

        // when
        notificationRedisSubscriber.onMessage(invalidPayload);

        // then
        verify(sseConnectionManager, never()).send(
            any(),
            any()
        );
    }

    @Test
    @DisplayName("onMessage 성공 - SSE 전송 실패해도 예외를 전파하지 않는다")
    void onMessage_doesNotThrow_whenSseSendFails() throws Exception {
        // given
        NotificationPubSubMessage message = new NotificationPubSubMessage(
            999L,
            3L,
            "manual-redis-pubsub-test-1",
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            "Redis Pub/Sub 다중 서버 알림 테스트입니다.",
            LocalDateTime.of(2026, 6, 11, 20, 20)
        );

        String payload = objectMapper.writeValueAsString(message);

        doThrow(new RuntimeException("SSE send error"))
            .when(sseConnectionManager)
            .send(
                any(),
                any()
            );

        // when & then
        assertThatCode(() -> notificationRedisSubscriber.onMessage(payload))
            .doesNotThrowAnyException();

        verify(sseConnectionManager).send(
            eq(3L),
            any(NotificationSseResponse.class)
        );
    }

}
