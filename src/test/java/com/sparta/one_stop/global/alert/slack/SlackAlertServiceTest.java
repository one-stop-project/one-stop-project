package com.sparta.one_stop.global.alert.slack;

import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    @DisplayName("Slack 알림 활성화 및 Webhook URL이 있으면 Slack 요청을 전송한다")
    @SuppressWarnings({
        "rawtypes",
        "unchecked"
    })
    void sendOutboxDeadAlert_enabledAndWebhookUrlExists_sendRequestSuccessfully() {
        // given
        OutboxEvent outboxEvent = createDeadOutboxEvent();
        String webhookUrl = "https://hooks.slack.com/services/test/webhook";

        RestClient.RequestBodyUriSpec requestBodyUriSpec =
            mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec =
            mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec =
            mock(RestClient.ResponseSpec.class);

        when(slackAlertProperties.isEnabled()).thenReturn(true);
        when(slackAlertProperties.hasWebhookUrl()).thenReturn(true);
        when(slackAlertProperties.getWebhookUrl()).thenReturn(webhookUrl);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(webhookUrl)).thenReturn(requestBodySpec);

        // body() 반환 타입이 RequestBodySpec으로 잡히므로 자기 자신을 반환하게 한다
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);

        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        // when
        slackAlertService.sendOutboxDeadAlert(outboxEvent);

        // then
        verify(restClient).post();
        verify(requestBodyUriSpec).uri(webhookUrl);

        ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);

        verify(requestBodySpec).body(bodyCaptor.capture());
        verify(requestBodySpec).retrieve();
        verify(responseSpec).toBodilessEntity();

        Map<String, String> body = bodyCaptor.getValue();

        assertThat(body).containsKey("text");

        String text = body.get("text");

        assertThat(text).contains("Outbox DEAD 이벤트 발생");
        assertThat(text).contains("payment-approved-1");
        assertThat(text).contains("PAYMENT_APPROVED");
        assertThat(text).contains("Kafka 발행 실패");
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
