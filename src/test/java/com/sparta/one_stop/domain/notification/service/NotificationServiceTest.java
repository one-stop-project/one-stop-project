package com.sparta.one_stop.domain.notification.service;

import com.sparta.one_stop.domain.notification.dto.pubsub.NotificationPubSubMessage;
import com.sparta.one_stop.domain.notification.entity.Notification;
import com.sparta.one_stop.domain.notification.publisher.NotificationRedisPublisher;
import com.sparta.one_stop.domain.notification.repository.NotificationRepository;
import com.sparta.one_stop.global.enums.notification.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationRedisPublisher notificationRedisPublisher;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("notify 성공 - 알림 저장 후 Redis Pub/Sub 발행을 요청한다")
    void notify_success() {
        // given
        Long userId = 1L;
        String eventId = "payment-approved-1";
        NotificationType type = NotificationType.PAYMENT_APPROVED;
        String title = "결제 완료";
        String message = "주문 #1 결제가 완료되었습니다.";

        when(notificationRepository.existsByEventId(eventId))
            .thenReturn(false);

        when(notificationRepository.saveAndFlush(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        notificationService.notify(
            userId,
            eventId,
            type,
            title,
            message
        );

        // then
        ArgumentCaptor<Notification> notificationCaptor =
            ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).saveAndFlush(notificationCaptor.capture());

        Notification savedNotification = notificationCaptor.getValue();

        assertThat(savedNotification.getUserId()).isEqualTo(userId);
        assertThat(savedNotification.getEventId()).isEqualTo(eventId);
        assertThat(savedNotification.getType()).isEqualTo(type);
        assertThat(savedNotification.getTitle()).isEqualTo(title);
        assertThat(savedNotification.getMessage()).isEqualTo(message);
        assertThat(savedNotification.isRead()).isFalse();

        ArgumentCaptor<NotificationPubSubMessage> messageCaptor =
            ArgumentCaptor.forClass(NotificationPubSubMessage.class);

        verify(notificationRedisPublisher).publish(messageCaptor.capture());

        NotificationPubSubMessage publishedMessage = messageCaptor.getValue();

        assertThat(publishedMessage.userId()).isEqualTo(userId);
        assertThat(publishedMessage.eventId()).isEqualTo(eventId);
        assertThat(publishedMessage.type()).isEqualTo(type);
        assertThat(publishedMessage.title()).isEqualTo(title);
        assertThat(publishedMessage.message()).isEqualTo(message);
    }

    @Test
    @DisplayName("notify 성공 - 트랜잭션 동기화가 비활성 상태이면 즉시 Redis Pub/Sub 발행한다")
    void notify_success_publishImmediately_whenTransactionSynchronizationInactive() {
        // given
        Long userId = 1L;
        String eventId = "payment-approved-2";
        NotificationType type = NotificationType.PAYMENT_APPROVED;
        String title = "결제 완료";
        String message = "주문 #2 결제가 완료되었습니다.";

        assertThat(TransactionSynchronizationManager.isSynchronizationActive())
            .isFalse();

        when(notificationRepository.existsByEventId(eventId))
            .thenReturn(false);

        when(notificationRepository.saveAndFlush(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        notificationService.notify(
            userId,
            eventId,
            type,
            title,
            message
        );

        // then
        ArgumentCaptor<NotificationPubSubMessage> messageCaptor =
            ArgumentCaptor.forClass(NotificationPubSubMessage.class);

        verify(notificationRedisPublisher).publish(messageCaptor.capture());

        NotificationPubSubMessage publishedMessage = messageCaptor.getValue();

        assertThat(publishedMessage.userId()).isEqualTo(userId);
        assertThat(publishedMessage.eventId()).isEqualTo(eventId);
        assertThat(publishedMessage.type()).isEqualTo(type);
        assertThat(publishedMessage.title()).isEqualTo(title);
        assertThat(publishedMessage.message()).isEqualTo(message);

        InOrder inOrder = inOrder(
            notificationRepository,
            notificationRedisPublisher
        );

        inOrder.verify(notificationRepository).saveAndFlush(any(Notification.class));
        inOrder.verify(notificationRedisPublisher).publish(any(NotificationPubSubMessage.class));
    }

    @Test
    @DisplayName("notify 성공 - afterCommit 콜백 내부 Redis Pub/Sub 발행 예외가 외부로 전파되지 않는다")
    void notify_success_whenAfterCommitPublishThrowsException_thenDoesNotPropagate() {
        // given
        Long userId = 1L;
        String eventId = "payment-approved-3";
        NotificationType type = NotificationType.PAYMENT_APPROVED;
        String title = "결제 완료";
        String message = "주문 #3 결제가 완료되었습니다.";

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }

        try {
            when(notificationRepository.existsByEventId(eventId))
                .thenReturn(false);

            when(notificationRepository.saveAndFlush(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            doThrow(new RuntimeException("Redis publish failed"))
                .when(notificationRedisPublisher)
                .publish(any(NotificationPubSubMessage.class));

            // when
            notificationService.notify(
                userId,
                eventId,
                type,
                title,
                message
            );

            TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);

            // then
            assertThatCode(synchronization::afterCommit)
                .doesNotThrowAnyException();

            verify(notificationRepository).saveAndFlush(any(Notification.class));
            verify(notificationRedisPublisher).publish(any(NotificationPubSubMessage.class));
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    @DisplayName("notify 스킵 - eventId 중복 시 저장하지 않고 Redis Pub/Sub 발행도 하지 않는다")
    void notify_skip_whenEventIdAlreadyExists() {
        // given
        String eventId = "payment-approved-1";

        when(notificationRepository.existsByEventId(eventId))
            .thenReturn(true);

        // when
        notificationService.notify(
            1L,
            eventId,
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            "주문 #1 결제가 완료되었습니다."
        );

        // then
        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
        verify(notificationRedisPublisher, never()).publish(any(NotificationPubSubMessage.class));
    }

    @Test
    @DisplayName("notify 스킵 - DataIntegrityViolationException 발생 시 Redis Pub/Sub 발행하지 않는다")
    void notify_skip_whenDataIntegrityViolationExceptionOccurs() {
        // given
        String eventId = "payment-approved-1";

        when(notificationRepository.existsByEventId(eventId))
            .thenReturn(false);

        doThrow(new DataIntegrityViolationException("duplicate eventId"))
            .when(notificationRepository)
            .saveAndFlush(any(Notification.class));

        // when
        notificationService.notify(
            1L,
            eventId,
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            "주문 #1 결제가 완료되었습니다."
        );

        // then
        verify(notificationRepository).saveAndFlush(any(Notification.class));
        verify(notificationRedisPublisher, never()).publish(any(NotificationPubSubMessage.class));
    }

}
