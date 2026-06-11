package com.sparta.one_stop.domain.notification.dto.pubsub;

import com.sparta.one_stop.domain.notification.dto.response.NotificationSseResponse;
import com.sparta.one_stop.domain.notification.entity.Notification;
import com.sparta.one_stop.global.enums.notification.NotificationType;

import java.time.LocalDateTime;

/**
 * Redis Pub/Sub으로 서버 간 전달할 알림 메시지
 *
 * 역할:
 * - Notification DB 저장 이후 Redis 채널로 publish 된다.
 * - 모든 서버 인스턴스가 이 메시지를 subscribe 한다.
 * - 각 서버는 userId를 기준으로 자기 서버에 연결된 SseEmitter가 있는지 확인한다.
 * - 연결이 있는 서버만 NotificationSseResponse로 변환해 SSE 전송한다.
 */
public record NotificationPubSubMessage(
    Long notificationId,
    Long userId,
    String eventId,
    NotificationType type,
    String title,
    String message,
    LocalDateTime createdAt
) {

    /**
     * Notification 엔티티를 Redis Pub/Sub 메시지로 변환한다.
     */
    public static NotificationPubSubMessage from(Notification notification) {
        return new NotificationPubSubMessage(
            notification.getId(),
            notification.getUserId(),
            notification.getEventId(),
            notification.getType(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getCreatedAt()
        );
    }

    /**
     * Redis Pub/Sub 메시지를 SSE 응답 DTO로 변환한다.
     */
    public NotificationSseResponse toSseResponse() {
        return new NotificationSseResponse(
            notificationId,
            type,
            title,
            message,
            createdAt
        );
    }

}
