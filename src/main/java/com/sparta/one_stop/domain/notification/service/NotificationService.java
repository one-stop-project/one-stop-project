package com.sparta.one_stop.domain.notification.service;

import com.sparta.one_stop.domain.notification.dto.pubsub.NotificationPubSubMessage;
import com.sparta.one_stop.domain.notification.entity.Notification;
import com.sparta.one_stop.domain.notification.publisher.NotificationRedisPublisher;
import com.sparta.one_stop.domain.notification.repository.NotificationRepository;
import com.sparta.one_stop.global.enums.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRedisPublisher notificationRedisPublisher;

    /**
     * 알림 생성 + Redis Pub/Sub 발행 예약
     *
     * - Kafka Consumer가 이벤트를 수신한 뒤 호출한다.
     * - eventId 중복 시 저장하지 않고 건너뛴다. (멱등성 보장)
     * - Notification은 DB에 먼저 저장한다.
     * - DB 트랜잭션이 정상 커밋된 이후 Redis Pub/Sub으로 알림 메시지를 발행한다.
     * - 실제 SSE 전송은 Redis Subscriber가 메시지를 수신한 뒤 처리한다.
     *
     * 처리 흐름:
     * 1. eventId 중복 여부 확인
     * 2. Notification DB 저장
     * 3. 트랜잭션 커밋 이후 Redis Pub/Sub publish
     * 4. 각 서버의 Redis Subscriber가 메시지 수신
     * 5. 해당 userId의 SseEmitter가 있는 서버만 SSE 전송
     *
     * 주의:
     * - Redis Pub/Sub은 메시지를 영속 저장하지 않는다.
     * - 따라서 알림 내역의 정합성은 Notification DB 저장으로 보장한다.
     * - Redis Pub/Sub은 다중 서버 간 실시간 전파 용도로만 사용한다.
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
            "알림 저장 완료 - notificationId: {}, eventId: {}, userId: {}, type: {}",
            savedNotification.getId(),
            savedNotification.getEventId(),
            savedNotification.getUserId(),
            savedNotification.getType()
        );

        publishAfterCommit(savedNotification);
    }

    /**
     * 트랜잭션 커밋 이후 Redis Pub/Sub 알림 메시지 발행
     *
     * 알림 DB 저장이 롤백되었는데 실시간 알림만 먼저 전송되는 상황을 막기 위해
     * Redis publish는 트랜잭션 커밋 이후에 수행한다.
     *
     * 처리 방식:
     * - 현재 트랜잭션 동기화가 활성화되어 있으면 afterCommit에서 발행한다.
     * - 트랜잭션 동기화가 없는 상황에서 호출되면 즉시 발행한다.
     * - Redis publish는 publishSafely()를 통해 수행하여 발행 실패가 외부로 전파되지 않도록 한다.
     *
     * 주의:
     * - Redis Pub/Sub은 메시지를 영속 저장하지 않는다.
     * - 따라서 알림 내역의 정합성은 Notification DB 저장으로 보장한다.
     * - Redis Pub/Sub은 다중 서버 간 실시간 전파 용도로만 사용한다.
     * - Redis 발행 실패는 알림 저장 결과에 영향을 주지 않도록 NotificationService에서 방어한다.
     */
    private void publishAfterCommit(Notification notification) {
        NotificationPubSubMessage message = NotificationPubSubMessage.from(notification);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishSafely(message);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishSafely(message);
                }
            }
        );
    }

    /**
     * Redis Pub/Sub 알림 메시지 안전 발행
     *
     * Redis publish 실패는 알림 저장 트랜잭션 결과에 영향을 주면 안 된다.
     * 따라서 발행 중 예외가 발생해도 외부로 전파하지 않고 로그만 남긴다.
     */
    private void publishSafely(NotificationPubSubMessage message) {
        try {
            notificationRedisPublisher.publish(message);
        } catch (Exception e) {
            log.error(
                "알림 Redis Pub/Sub 발행 실패 - eventId: {}, userId: {}",
                message.eventId(),
                message.userId(),
                e
            );
        }
    }

}
