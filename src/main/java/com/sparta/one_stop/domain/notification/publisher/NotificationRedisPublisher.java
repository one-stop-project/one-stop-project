package com.sparta.one_stop.domain.notification.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.notification.dto.pubsub.NotificationPubSubMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRedisPublisher {

    public static final String NOTIFICATION_SSE_CHANNEL = "notification:sse";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Redis Pub/Sub 채널로 알림 메시지 발행
     *
     * - Notification DB 저장 이후 호출된다.
     * - Redis Pub/Sub은 실시간 전파 용도이므로 발행 실패가 알림 저장에 영향을 주면 안 된다.
     * - 따라서 발행 실패 시 예외를 전파하지 않고 로그만 남긴다.
     */
    public void publish(NotificationPubSubMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);

            Long subscriberCount = stringRedisTemplate.convertAndSend(
                NOTIFICATION_SSE_CHANNEL,
                payload
            );

            log.info(
                "Redis 알림 메시지 발행 완료 - channel: {}, notificationId: {}, userId: {}, subscriberCount: {}",
                NOTIFICATION_SSE_CHANNEL,
                message.notificationId(),
                message.userId(),
                subscriberCount
            );
        } catch (JsonProcessingException e) {
            log.error(
                "Redis 알림 메시지 직렬화 실패 - notificationId: {}, userId: {}",
                message.notificationId(),
                message.userId(),
                e
            );
        } catch (RuntimeException e) {
            log.error(
                "Redis 알림 메시지 발행 실패 - notificationId: {}, userId: {}",
                message.notificationId(),
                message.userId(),
                e
            );
        }
    }

}
