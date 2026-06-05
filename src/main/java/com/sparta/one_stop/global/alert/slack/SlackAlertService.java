package com.sparta.one_stop.global.alert.slack;

import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackAlertService {

    private final SlackAlertProperties slackAlertProperties;

    private final RestClient restClient = RestClient.create();

    /**
     * OutboxEvent DEAD 전이 알림
     * - Kafka 발행 재시도 한도 초과로 DEAD 상태가 된 이벤트를 Slack으로 알린다
     * - Slack 전송 실패가 Outbox 상태 저장에 영향을 주지 않도록 예외를 전파하지 않는다
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

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "-";
        }

        return dateTime.toString();
    }

    private String formatErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "-";
        }

        return errorMessage;
    }

}
