package com.sparta.one_stop.domain.notification.service;

import com.sparta.one_stop.domain.notification.dto.response.NotificationSseResponse;
import com.sparta.one_stop.domain.notification.entity.Notification;
import com.sparta.one_stop.domain.notification.repository.NotificationRepository;
import com.sparta.one_stop.global.enums.notification.NotificationType;
import com.sparta.one_stop.global.sse.SseConnectionManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SseConnectionManager sseConnectionManager;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    @DisplayName("notify 성공 - 알림 저장 후 SSE 전송을 호출한다")
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

        verify(sseConnectionManager).send(
            eq(userId),
            any(NotificationSseResponse.class)
        );
    }

    @Test
    @DisplayName("notify 스킵 - eventId 중복 시 저장하지 않는다")
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
        verify(sseConnectionManager, never()).send(
            any(),
            any()
        );
    }

    @Test
    @DisplayName("notify 스킵 - DataIntegrityViolationException 발생 시 SSE 전송하지 않는다")
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
        verify(sseConnectionManager, never()).send(
            any(),
            any()
        );
    }

    @Test
    @DisplayName("notify 성공 - SSE 전송 실패해도 예외를 전파하지 않는다")
    void notify_success_whenSseSendFails() {
        // given
        Long userId = 1L;
        String eventId = "payment-approved-1";

        when(notificationRepository.existsByEventId(eventId))
            .thenReturn(false);

        when(notificationRepository.saveAndFlush(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(new RuntimeException("sse error"))
            .when(sseConnectionManager)
            .send(
                any(),
                any()
            );

        // when & then
        assertThatCode(() -> notificationService.notify(
            userId,
            eventId,
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            "주문 #1 결제가 완료되었습니다."
        )).doesNotThrowAnyException();

        verify(notificationRepository).saveAndFlush(any(Notification.class));
        verify(sseConnectionManager).send(
            eq(userId),
            any(NotificationSseResponse.class)
        );
    }

    @Test
    @DisplayName("notify 성공 - SSE 연결이 없어도 알림 저장은 성공한다")
    void notify_success_whenSseConnectionDoesNotExist() {
        // given
        Long userId = 1L;
        String eventId = "payment-approved-1";

        when(notificationRepository.existsByEventId(eventId))
            .thenReturn(false);

        when(notificationRepository.saveAndFlush(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        notificationService.notify(
            userId,
            eventId,
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            "주문 #1 결제가 완료되었습니다."
        );

        // then
        verify(notificationRepository).saveAndFlush(any(Notification.class));
        verify(sseConnectionManager).send(
            eq(userId),
            any(NotificationSseResponse.class)
        );
    }

}
