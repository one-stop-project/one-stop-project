package com.sparta.one_stop.global.alert.slack;

import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackAlertServiceTest {

    private final SlackAlertProperties slackAlertProperties =
        mock(SlackAlertProperties.class);

    private final RestClient restClient =
        mock(RestClient.class);

    private final SlackAlertService slackAlertService =
        new SlackAlertService(
            slackAlertProperties,
            restClient
        );

    @Test
    @DisplayName("Slack 알림이 비활성화되어 있으면 Slack 요청을 보내지 않는다")
    void sendOutboxDeadAlert_disabled_doesNotSendRequest() {
        // given
        OutboxEvent outboxEvent = createDeadOutboxEvent();

        when(slackAlertProperties.isEnabled()).thenReturn(false);

        // when
        slackAlertService.sendOutboxDeadAlert(outboxEvent);

        // then
        verify(restClient, never()).post();
    }

    @Test
    @DisplayName("Slack Webhook URL이 없으면 Slack 요청을 보내지 않는다")
    void sendOutboxDeadAlert_webhookUrlMissing_doesNotSendRequest() {
        // given
        OutboxEvent outboxEvent = createDeadOutboxEvent();

        when(slackAlertProperties.isEnabled()).thenReturn(true);
        when(slackAlertProperties.hasWebhookUrl()).thenReturn(false);

        // when
        slackAlertService.sendOutboxDeadAlert(outboxEvent);

        // then
        verify(restClient, never()).post();
    }

    private OutboxEvent createDeadOutboxEvent() {
        OutboxEvent outboxEvent = OutboxEvent.paymentApproved(
            "payment-approved-1",
            1L,
            """
                {
                  "eventId": "payment-approved-1",
                  "eventType": "PAYMENT_APPROVED",
                  "orderId": 1,
                  "paymentId": 1,
                  "userId": 1,
                  "finalPrice": 10000,
                  "approvedAt": "2026-06-09T10:00:00"
                }
                """
        );

        outboxEvent.markProcessing();
        outboxEvent.markFailure("Kafka 발행 실패", 3);

        return outboxEvent;
    }

}
