package com.sparta.one_stop.domain.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentApprovedConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * 결제 승인 이벤트 수신
     * - payload 역직렬화 실패는 재시도해도 복구 가능성이 낮으므로 로그만 남기고 스킵한다
     * - 알림 저장 등 처리 실패는 예외를 전파하여 Kafka 재처리 대상이 되도록 한다
     * - Outbox 상태에는 영향을 주지 않는다
     */
    @KafkaListener(
        topics = "payment.approved",
        groupId = "one-stop-notification-group"
    )
    public void onPaymentApproved(String payload) {
        Optional<PaymentApprovedEventPayload> eventOptional = deserialize(payload);

        if (eventOptional.isEmpty()) {
            return;
        }

        PaymentApprovedEventPayload event = eventOptional.get();

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
    }

    /**
     * payload 역직렬화
     * - JSON 형식 오류는 재시도해도 복구 가능성이 낮으므로 빈 Optional을 반환한다
     */
    private Optional<PaymentApprovedEventPayload> deserialize(String payload) {
        try {
            return Optional.of(
                objectMapper.readValue(
                    payload,
                    PaymentApprovedEventPayload.class
                )
            );
        } catch (JsonProcessingException e) {
            log.error(
                "결제 승인 이벤트 역직렬화 실패 - payload: {}",
                payload, e
            );

            return Optional.empty();
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
