package com.sparta.one_stop.domain.notification.subscriber;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.notification.dto.pubsub.NotificationPubSubMessage;
import com.sparta.one_stop.domain.notification.dto.response.NotificationSseResponse;
import com.sparta.one_stop.global.sse.SseConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRedisSubscriber {

    private final ObjectMapper objectMapper;
    private final SseConnectionManager sseConnectionManager;

    /**
     * Redis Pub/Sub 알림 메시지 수신
     *
     * - NotificationRedisPublisher가 발행한 JSON 문자열을 수신한다.
     * - 수신한 메시지를 NotificationPubSubMessage로 역직렬화한다.
     * - 현재 서버에 해당 userId의 SseEmitter 연결이 있으면 SSE로 전송한다.
     * - 현재 서버에 연결이 없거나 전송에 실패해도 예외를 전파하지 않는다.
     *
     * 다중 서버 환경에서의 동작:
     * - 모든 서버 인스턴스가 같은 Redis 채널을 구독한다.
     * - 각 서버는 메시지를 받은 뒤 자기 메모리에 해당 사용자의 SseEmitter가 있는지 확인한다.
     * - SseEmitter가 있는 서버만 실제 SSE 알림을 전송한다.
     */
    public void onMessage(String payload) {
        NotificationPubSubMessage message = deserialize(payload);

        if (message == null) {
            return;
        }

        sendSse(message);
    }

    /**
     * Redis Pub/Sub payload 역직렬화
     *
     * - Redis Pub/Sub에는 JSON 문자열로 메시지를 발행한다.
     * - JSON 형식이 잘못된 메시지는 재처리해도 복구 가능성이 낮으므로 로그만 남기고 스킵한다.
     */
    private NotificationPubSubMessage deserialize(String payload) {
        try {
            return objectMapper.readValue(
                payload,
                NotificationPubSubMessage.class
            );
        } catch (JsonProcessingException e) {
            log.error(
                "Redis 알림 메시지 역직렬화 실패 - payload: {}",
                payload,
                e
            );

            return null;
        }
    }

    /**
     * 현재 서버에 연결된 사용자에게 SSE 알림 전송
     *
     * - SseConnectionManager는 현재 서버 메모리에 저장된 SseEmitter만 관리한다.
     * - 따라서 해당 userId의 연결이 현재 서버에 없으면 전송되지 않을 수 있다.
     * - 이 경우 다른 서버가 같은 Redis 메시지를 수신한 뒤 자기 메모리의 emitter로 전송한다.
     * - 전송 실패는 실시간 알림 실패일 뿐이며, Notification DB 저장 결과에는 영향을 주지 않는다.
     */
    private void sendSse(NotificationPubSubMessage message) {
        try {
            NotificationSseResponse response = message.toSseResponse();

            sseConnectionManager.send(
                message.userId(),
                response
            );

            log.info(
                "Redis 알림 메시지 SSE 전송 완료 - notificationId: {}, eventId: {}, userId: {}",
                message.notificationId(),
                message.eventId(),
                message.userId()
            );
        } catch (Exception e) {
            log.warn(
                "Redis 알림 메시지 SSE 전송 실패 또는 현재 서버에 연결 없음 - notificationId: {}, eventId: {}, userId: {}",
                message.notificationId(),
                message.eventId(),
                message.userId(),
                e
            );
        }
    }

}
