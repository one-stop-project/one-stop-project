package com.sparta.one_stop.domain.notification.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.notification.dto.pubsub.NotificationPubSubMessage;
import com.sparta.one_stop.global.enums.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationRedisPublisherTest {

    private StringRedisTemplate stringRedisTemplate;
    private ObjectMapper objectMapper;
    private NotificationRedisPublisher notificationRedisPublisher;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        objectMapper = mock(ObjectMapper.class);

        notificationRedisPublisher = new NotificationRedisPublisher(
            stringRedisTemplate,
            objectMapper
        );
    }

    @Test
    @DisplayName("publish 성공 - Redis Pub/Sub 채널로 메시지를 발행한다")
    void publish_success() throws Exception {
        // given
        NotificationPubSubMessage message = createMessage();
        String payload = "{\"notificationId\":999}";

        when(objectMapper.writeValueAsString(message))
            .thenReturn(payload);

        when(stringRedisTemplate.convertAndSend(
            NotificationRedisPublisher.NOTIFICATION_SSE_CHANNEL,
            payload
        )).thenReturn(2L);

        // when
        notificationRedisPublisher.publish(message);

        // then
        verify(stringRedisTemplate).convertAndSend(
            NotificationRedisPublisher.NOTIFICATION_SSE_CHANNEL,
            payload
        );
    }

    @Test
    @DisplayName("publish 실패 - 직렬화 실패 시 Redis 발행을 시도하지 않고 예외를 전파하지 않는다")
    void publish_skip_whenSerializationFails() throws Exception {
        // given
        NotificationPubSubMessage message = createMessage();

        when(objectMapper.writeValueAsString(message))
            .thenThrow(new JsonProcessingException("serialize error") {
            });

        // when & then
        assertThatCode(() -> notificationRedisPublisher.publish(message))
            .doesNotThrowAnyException();

        verify(stringRedisTemplate, never()).convertAndSend(
            any(String.class),
            any(String.class)
        );
    }

    @Test
    @DisplayName("publish 실패 - Redis 발행 실패 시 예외를 전파하지 않는다")
    void publish_doesNotThrow_whenRedisPublishFails() throws Exception {
        // given
        NotificationPubSubMessage message = createMessage();
        String payload = "{\"notificationId\":999}";

        when(objectMapper.writeValueAsString(message))
            .thenReturn(payload);

        when(stringRedisTemplate.convertAndSend(
            eq(NotificationRedisPublisher.NOTIFICATION_SSE_CHANNEL),
            eq(payload)
        )).thenThrow(new RuntimeException("Redis publish error"));

        // when & then
        assertThatCode(() -> notificationRedisPublisher.publish(message))
            .doesNotThrowAnyException();

        verify(stringRedisTemplate).convertAndSend(
            NotificationRedisPublisher.NOTIFICATION_SSE_CHANNEL,
            payload
        );
    }

    private NotificationPubSubMessage createMessage() {
        return new NotificationPubSubMessage(
            999L,
            3L,
            "manual-redis-pubsub-test-1",
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            "Redis Pub/Sub 다중 서버 알림 테스트입니다.",
            LocalDateTime.of(2026, 6, 11, 20, 20)
        );
    }

}
