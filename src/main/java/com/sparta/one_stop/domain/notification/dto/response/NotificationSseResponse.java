package com.sparta.one_stop.domain.notification.dto.response;

import com.sparta.one_stop.domain.notification.entity.Notification;
import com.sparta.one_stop.global.enums.notification.NotificationType;

import java.time.LocalDateTime;

public record NotificationSseResponse(
    Long notificationId,
    NotificationType type,
    String title,
    String message,
    LocalDateTime createdAt
) {

    /**
     * Notification 엔티티를 SSE 전송용 응답 DTO로 변환한다
     */
    public static NotificationSseResponse from(Notification notification) {
        return new NotificationSseResponse(
            notification.getId(),
            notification.getType(),
            notification.getTitle(),
            notification.getMessage(),
            notification.getCreatedAt()
        );
    }

}
