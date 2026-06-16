package com.sparta.one_stop.domain.notification.entity;

import com.sparta.one_stop.global.enums.notification.NotificationType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationTest {

    @Test
    @DisplayName("create 성공 - userId, eventId, type, title, message, isRead=false가 설정된다")
    void create_success() {
        // given
        Long userId = 1L;
        String eventId = "payment-approved-1";
        NotificationType type = NotificationType.PAYMENT_APPROVED;
        String title = "결제 완료";
        String message = "주문 #1 결제가 완료되었습니다.";

        // when
        Notification notification = Notification.create(
            userId,
            eventId,
            type,
            title,
            message
        );

        // then
        assertThat(notification.getUserId()).isEqualTo(userId);
        assertThat(notification.getEventId()).isEqualTo(eventId);
        assertThat(notification.getType()).isEqualTo(type);
        assertThat(notification.getTitle()).isEqualTo(title);
        assertThat(notification.getMessage()).isEqualTo(message);
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    @DisplayName("create 실패 - userId가 null이면 예외가 발생한다")
    void create_fail_whenUserIdIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> Notification.create(
                null,
                "payment-approved-1",
                NotificationType.PAYMENT_APPROVED,
                "결제 완료",
                "주문 #1 결제가 완료되었습니다."
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_020);
    }

    @Test
    @DisplayName("create 실패 - eventId가 null이면 예외가 발생한다")
    void create_fail_whenEventIdIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> Notification.create(
                1L,
                null,
                NotificationType.PAYMENT_APPROVED,
                "결제 완료",
                "주문 #1 결제가 완료되었습니다."
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_021);
    }

    @Test
    @DisplayName("create 실패 - eventId가 blank이면 예외가 발생한다")
    void create_fail_whenEventIdIsBlank() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> Notification.create(
                1L,
                " ",
                NotificationType.PAYMENT_APPROVED,
                "결제 완료",
                "주문 #1 결제가 완료되었습니다."
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_021);
    }

    @Test
    @DisplayName("create 실패 - type이 null이면 예외가 발생한다")
    void create_fail_whenTypeIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> Notification.create(
                1L,
                "payment-approved-1",
                null,
                "결제 완료",
                "주문 #1 결제가 완료되었습니다."
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_022);
    }

    @Test
    @DisplayName("create 실패 - title이 null이면 예외가 발생한다")
    void create_fail_whenTitleIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> Notification.create(
                1L,
                "payment-approved-1",
                NotificationType.PAYMENT_APPROVED,
                null,
                "주문 #1 결제가 완료되었습니다."
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_023);
    }

    @Test
    @DisplayName("create 실패 - title이 blank이면 예외가 발생한다")
    void create_fail_whenTitleIsBlank() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> Notification.create(
                1L,
                "payment-approved-1",
                NotificationType.PAYMENT_APPROVED,
                " ",
                "주문 #1 결제가 완료되었습니다."
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_023);
    }

    @Test
    @DisplayName("create 실패 - message가 null이면 예외가 발생한다")
    void create_fail_whenMessageIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> Notification.create(
                1L,
                "payment-approved-1",
                NotificationType.PAYMENT_APPROVED,
                "결제 완료",
                null
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_024);
    }

    @Test
    @DisplayName("create 실패 - message가 blank이면 예외가 발생한다")
    void create_fail_whenMessageIsBlank() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> Notification.create(
                1L,
                "payment-approved-1",
                NotificationType.PAYMENT_APPROVED,
                "결제 완료",
                " "
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_024);
    }

    @Test
    @DisplayName("markRead 성공 - isRead가 true로 변경된다")
    void markRead_success() {
        // given
        Notification notification = notification();

        // when
        notification.markRead();

        // then
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("markRead 성공 - 이미 읽은 알림을 다시 읽음 처리해도 true를 유지한다")
    void markRead_success_whenAlreadyRead() {
        // given
        Notification notification = notification();
        notification.markRead();

        // when
        notification.markRead();

        // then
        assertThat(notification.isRead()).isTrue();
    }

    private Notification notification() {
        return Notification.create(
            1L,
            "payment-approved-1",
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            "주문 #1 결제가 완료되었습니다."
        );
    }

}
