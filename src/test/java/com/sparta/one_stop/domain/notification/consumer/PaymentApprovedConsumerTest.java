package com.sparta.one_stop.domain.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.one_stop.domain.notification.service.NotificationService;
import com.sparta.one_stop.domain.payment.event.PaymentApprovedEventPayload;
import com.sparta.one_stop.global.enums.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentApprovedConsumerTest {

    @Mock
    private NotificationService notificationService;

    private PaymentApprovedConsumer paymentApprovedConsumer;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        paymentApprovedConsumer = new PaymentApprovedConsumer(
            notificationService,
            objectMapper
        );
    }

    @Test
    @DisplayName("onPaymentApproved 성공 - 정상 payload 수신 시 NotificationService를 호출한다")
    void onPaymentApproved_success() throws Exception {
        // given
        String payload = validPayload();

        // when
        paymentApprovedConsumer.onPaymentApproved(payload);

        // then
        verify(notificationService).notify(
            2L,
            "payment-approved-6",
            NotificationType.PAYMENT_APPROVED,
            "결제 완료",
            "주문 #11 결제가 완료되었습니다. (결제 금액: 1,992,000원)"
        );
    }

    @Test
    @DisplayName("onPaymentApproved 스킵 - 역직렬화 실패 시 NotificationService를 호출하지 않는다")
    void onPaymentApproved_skip_whenDeserializeFails() {
        // given
        String invalidPayload = "{ invalid-json }";

        // when & then
        assertThatCode(() -> paymentApprovedConsumer.onPaymentApproved(invalidPayload))
            .doesNotThrowAnyException();

        verify(notificationService, never()).notify(
            anyLong(),
            anyString(),
            any(NotificationType.class),
            anyString(),
            anyString()
        );
    }

    @Test
    @DisplayName("onPaymentApproved 성공 - 알림 메시지에 주문번호와 결제금액이 포함된다")
    void onPaymentApproved_success_containsOrderIdAndFinalPriceInMessage() throws Exception {
        // given
        String payload = validPayload();

        // when
        paymentApprovedConsumer.onPaymentApproved(payload);

        // then
        ArgumentCaptor<String> messageCaptor =
            ArgumentCaptor.forClass(String.class);

        verify(notificationService).notify(
            eq(2L),
            eq("payment-approved-6"),
            eq(NotificationType.PAYMENT_APPROVED),
            eq("결제 완료"),
            messageCaptor.capture()
        );

        String message = messageCaptor.getValue();

        assertThat(message).contains("주문 #11");
        assertThat(message).contains("1,992,000원");
    }

    @Test
    @DisplayName("onPaymentApproved 실패 - NotificationService 처리 실패 시 예외를 전파한다")
    void onPaymentApproved_fail_whenNotificationServiceFails() throws Exception {
        // given
        String payload = validPayload();

        doThrow(new RuntimeException("notification error"))
            .when(notificationService)
            .notify(
                anyLong(),
                anyString(),
                any(NotificationType.class),
                anyString(),
                anyString()
            );

        // when & then
        assertThatThrownBy(() -> paymentApprovedConsumer.onPaymentApproved(payload))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("notification error");
    }

    private String validPayload() throws Exception {
        PaymentApprovedEventPayload eventPayload = new PaymentApprovedEventPayload(
            "payment-approved-6",
            "PAYMENT_APPROVED",
            11L,
            6L,
            2L,
            1_992_000L,
            LocalDateTime.of(
                2026,
                6,
                4,
                19,
                19,
                11
            )
        );

        return objectMapper.writeValueAsString(eventPayload);
    }

}
