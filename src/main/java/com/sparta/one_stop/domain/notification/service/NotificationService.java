package com.sparta.one_stop.domain.notification.service;

import com.sparta.one_stop.domain.notification.dto.response.NotificationSseResponse;
import com.sparta.one_stop.domain.notification.entity.Notification;
import com.sparta.one_stop.domain.notification.repository.NotificationRepository;
import com.sparta.one_stop.global.enums.notification.NotificationType;
import com.sparta.one_stop.global.sse.SseConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SseConnectionManager sseConnectionManager;

    /**
     * 알림 생성 + SSE 전송
     * - Kafka Consumer가 이벤트를 수신한 뒤 호출한다
     * - eventId 중복 시 저장하지 않고 건너뛴다 (멱등성 보장)
     * - 알림 저장 후 SSE 연결이 있으면 실시간 전송을 시도한다
     * - SSE 전송 실패가 알림 저장에 영향을 주지 않는다
     */
    @Transactional
    public void notify(
        Long userId,
        String eventId,
        NotificationType type,
        String title,
        String message
    ) {
        if (notificationRepository.existsByEventId(eventId)) {
            log.info("이미 처리된 알림 이벤트 - eventId: {}", eventId);
            return;
        }

        Notification notification = Notification.create(
            userId,
            eventId,
            type,
            title,
            message
        );

        Notification savedNotification;

        try {
            savedNotification = notificationRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException e) {
            log.info("중복 알림 이벤트 저장 스킵 - eventId: {}", eventId);
            return;
        }

        log.info(
            "알림 저장 완료 - eventId: {}, userId: {}, type: {}",
            eventId, userId, type
        );

        sendSse(userId, savedNotification);
    }

    /**
     * SSE 실시간 알림 전송
     * - 전송 실패 시 로그만 기록하고 예외를 전파하지 않는다
     * - SSE 전송 실패 여부와 관계없이 알림 저장은 유지된다
     */
    private void sendSse(Long userId, Notification notification) {
        try {
            NotificationSseResponse response = NotificationSseResponse.from(notification);

            sseConnectionManager.send(userId, response);
        } catch (Exception e) {
            log.warn(
                "SSE 알림 전송 실패 - eventId: {}, userId: {}",
                notification.getEventId(), userId, e
            );
        }
    }

}
