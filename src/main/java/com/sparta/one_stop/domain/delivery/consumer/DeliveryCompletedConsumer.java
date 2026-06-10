package com.sparta.one_stop.domain.delivery.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.delivery.event.DeliveryCompletedEventPayload;
import com.sparta.one_stop.domain.point.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCompletedConsumer {

    private final PointService pointService;
    private final ObjectMapper objectMapper;

    /**
     * 배송 완료 이벤트 수신
     * - payload 역직렬화 실패는 로그만 남기고 스킵
     * - 포인트 적립 실패는 예외를 전파하여 Kafka 재처리 대상이 되도록 한다
     */
    @KafkaListener(
        topics = "delivery.completed",
        groupId = "one-stop-point-group"
    )
    public void onDeliveryCompleted(String payload) {

        Optional<DeliveryCompletedEventPayload> eventOptional =
            deserialize(payload);

        if (eventOptional.isEmpty()) {
            return;
        }

        DeliveryCompletedEventPayload event =
            eventOptional.get();

        log.info(
            "배송 완료 이벤트 수신 - eventId: {}, orderId: {}, userId: {}",
            event.eventId(),
            event.orderId(),
            event.userId()
        );

        pointService.earnPointByDelivery(
            event.orderId()
        );
    }

    /**
     * payload 역직렬화
     */
    private Optional<DeliveryCompletedEventPayload> deserialize(
        String payload
    ) {
        try {

            return Optional.of(
                objectMapper.readValue(
                    payload,
                    DeliveryCompletedEventPayload.class
                )
            );

        } catch (JsonProcessingException e) {

            log.error(
                "배송 완료 이벤트 역직렬화 실패 - payload: {}",
                payload,
                e
            );

            return Optional.empty();
        }
    }
}
