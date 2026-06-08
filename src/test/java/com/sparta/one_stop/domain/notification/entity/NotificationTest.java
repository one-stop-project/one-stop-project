package com.sparta.one_stop.domain.notification.entity;

import com.sparta.one_stop.global.enums.notification.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        // when & then
        assertThatThrownBy(() -> Notification.create(
            null,
            "payment-approved-1",
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            "주문 #1 결제가 완료되었습니다."
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("알림 대상 사용자는 필수입니다.");
    }

    @Test
    @DisplayName("create 실패 - eventId가 null이면 예외가 발생한다")
    void create_fail_whenEventIdIsNull() {
        // when & then
        assertThatThrownBy(() -> Notification.create(
            1L,
            null,
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            "주문 #1 결제가 완료되었습니다."
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("이벤트 ID는 필수입니다.");
    }

    @Test
    @DisplayName("create 실패 - eventId가 blank이면 예외가 발생한다")
    void create_fail_whenEventIdIsBlank() {
        // when & then
        assertThatThrownBy(() -> Notification.create(
            1L,
            " ",
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            "주문 #1 결제가 완료되었습니다."
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("이벤트 ID는 필수입니다.");
    }

    @Test
    @DisplayName("create 실패 - type이 null이면 예외가 발생한다")
    void create_fail_whenTypeIsNull() {
        // when & then
        assertThatThrownBy(() -> Notification.create(
            1L,
            "payment-approved-1",
            null,
            "결제 완료",
            "주문 #1 결제가 완료되었습니다."
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("알림 유형은 필수입니다.");
    }

    @Test
    @DisplayName("create 실패 - title이 null이면 예외가 발생한다")
    void create_fail_whenTitleIsNull() {
        // when & then
        assertThatThrownBy(() -> Notification.create(
            1L,
            "payment-approved-1",
            NotificationType.PAYMENT_APPROVED,
            null,
            "주문 #1 결제가 완료되었습니다."
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("알림 제목은 필수입니다.");
    }

    @Test
    @DisplayName("create 실패 - title이 blank이면 예외가 발생한다")
    void create_fail_whenTitleIsBlank() {
        // when & then
        assertThatThrownBy(() -> Notification.create(
            1L,
            "payment-approved-1",
            NotificationType.PAYMENT_APPROVED,
            " ",
            "주문 #1 결제가 완료되었습니다."
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("알림 제목은 필수입니다.");
    }

    @Test
    @DisplayName("create 실패 - message가 null이면 예외가 발생한다")
    void create_fail_whenMessageIsNull() {
        // when & then
        assertThatThrownBy(() -> Notification.create(
            1L,
            "payment-approved-1",
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("알림 내용은 필수입니다.");
    }

    @Test
    @DisplayName("create 실패 - message가 blank이면 예외가 발생한다")
    void create_fail_whenMessageIsBlank() {
        // when & then
        assertThatThrownBy(() -> Notification.create(
            1L,
            "payment-approved-1",
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            " "
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("알림 내용은 필수입니다.");
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
