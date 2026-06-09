package com.sparta.one_stop.global.alert.slack;

import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
public class SlackAlertService {

    private final SlackAlertProperties slackAlertProperties;
    private final RestClient restClient;

    /**
     * SlackAlertService 생성자
     * - Slack 알림 설정값은 SlackAlertProperties에서 주입받는다.
     * - RestClient는 Service 내부에서 직접 생성하지 않고 Slack 전용 Bean을 주입받는다.
     * - @Qualifier("slackRestClient")를 사용하여 다른 RestClient Bean과 충돌하지 않도록 한다.
     * - 테스트에서는 RestClient mock을 주입하여 실제 Slack 요청 없이 검증할 수 있다.
     */
    public SlackAlertService(
        SlackAlertProperties slackAlertProperties,
        @Qualifier("slackRestClient") RestClient restClient
    ) {
        this.slackAlertProperties = slackAlertProperties;
        this.restClient = restClient;
    }

    /**
     * OutboxEvent DEAD 전이 알림
     * - Kafka 발행 재시도 한도 초과로 DEAD 상태가 된 이벤트를 Slack으로 알린다.
     * - Slack 알림이 비활성화되어 있으면 전송하지 않는다.
     * - Slack Webhook URL이 설정되어 있지 않으면 전송하지 않는다.
     * - Slack 전송 실패가 Outbox 상태 저장에 영향을 주지 않도록 예외를 전파하지 않는다.
     */
    public void sendOutboxDeadAlert(OutboxEvent outboxEvent) {
        if (!slackAlertProperties.isEnabled()) {
            log.debug(
                "Slack 알림 비활성화 - eventId: {}",
                outboxEvent.getEventId()
            );
            return;
        }

        if (!slackAlertProperties.hasWebhookUrl()) {
            log.warn(
                "Slack Webhook URL 미설정 - eventId: {}",
                outboxEvent.getEventId()
            );
            return;
        }

        try {
            String message = createOutboxDeadMessage(outboxEvent);

            restClient.post()
                .uri(slackAlertProperties.getWebhookUrl())
                .body(Map.of("text", message))
                .retrieve()
                .toBodilessEntity();

            log.info(
                "Slack Outbox DEAD 알림 전송 성공 - eventId: {}",
                outboxEvent.getEventId()
            );
        } catch (Exception e) {
            log.error(
                "Slack Outbox DEAD 알림 전송 실패 - eventId: {}",
                outboxEvent.getEventId(),
                e
            );
        }
    }

    /**
     * Slack 알림 메시지 생성
     * - DEAD 상태가 된 OutboxEvent의 주요 식별자와 실패 정보를 Slack 메시지 형식으로 변환한다.
     * - 운영자가 eventId, topic, retryCount, lastErrorMessage를 보고 수동 복구 여부를 판단할 수 있도록 한다.
     */
    private String createOutboxDeadMessage(OutboxEvent outboxEvent) {
        return """
            🚨 *Outbox DEAD 이벤트 발생*

            *eventId*: `%s`
            *eventType*: `%s`
            *aggregateType*: `%s`
            *aggregateId*: `%s`
            *topic*: `%s`
            *partitionKey*: `%s`
            *retryCount*: `%d`
            *processedAt*: `%s`
            *lastErrorMessage*:
            ```%s```

            조치 필요: Kafka 상태와 OutboxEvent를 확인한 뒤 필요 시 PENDING으로 수동 복구해주세요.
            """.formatted(
            outboxEvent.getEventId(),
            outboxEvent.getEventType(),
            outboxEvent.getAggregateType(),
            outboxEvent.getAggregateId(),
            outboxEvent.getTopic(),
            outboxEvent.getPartitionKey(),
            outboxEvent.getRetryCount(),
            formatDateTime(outboxEvent.getProcessedAt()),
            formatErrorMessage(outboxEvent.getLastErrorMessage())
        );
    }

    /**
     * Slack 메시지용 날짜 포맷
     * - processedAt이 없으면 "-"로 표시한다.
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }

        return dateTime.toString();
    }

    /**
     * Slack 메시지용 에러 메시지 포맷
     * - 실패 메시지가 없으면 "-"로 표시한다.
     */
    private String formatErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "-";
        }

        return errorMessage;
    }

}
