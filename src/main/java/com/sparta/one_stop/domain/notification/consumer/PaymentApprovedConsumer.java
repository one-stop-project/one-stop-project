package com.sparta.one_stop.domain.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.notification.service.NotificationService;
import com.sparta.one_stop.domain.payment.event.PaymentApprovedEventPayload;
import com.sparta.one_stop.global.enums.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentApprovedConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * 결제 승인 이벤트 수신
     * - payment.approved 토픽을 구독하여 결제 완료 알림을 생성한다
     * - Consumer Group은 one-stop-notification-group으로 알림 전용 그룹을 사용한다
     * - payload 역직렬화 실패 또는 처리 실패 시 로그를 기록하고 예외를 전파하지 않는다
     * - Outbox 상태에는 영향을 주지 않는다
     */
    @KafkaListener(
        topics = "payment.approved",
        groupId = "one-stop-notification-group"
    )
    public void onPaymentApproved(String payload) {
        try {
            PaymentApprovedEventPayload event = objectMapper.readValue(
                payload,
                PaymentApprovedEventPayload.class
            );

            log.info(
                "결제 승인 이벤트 수신 - eventId: {}, orderId: {}, userId: {}",
                event.eventId(), event.orderId(), event.userId()
            );

            notificationService.notify(
                event.userId(),
                event.eventId(),
                NotificationType.PAYMENT_APPROVED,
                "결제 완료",
                formatMessage(event)
            );
        } catch (Exception e) {
            log.error(
                "결제 승인 이벤트 처리 실패 - payload: {}",
                payload, e
            );
        }
    }

    /**
     * 결제 완료 알림 메시지 생성
     * - 예: "주문 #13 결제가 완료되었습니다. (결제 금액: 192,000원)"
     */
    private String formatMessage(PaymentApprovedEventPayload event) {
        String formattedPrice = NumberFormat.getNumberInstance(Locale.KOREA)
            .format(event.finalPrice());

        return String.format(
            "주문 #%d 결제가 완료되었습니다. (결제 금액: %s원)",
            event.orderId(),
            formattedPrice
        );
    }

}
