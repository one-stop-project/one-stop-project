package com.sparta.one_stop.global.outbox.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxKafkaProducerTest {

    private final KafkaTemplate<String, String> kafkaTemplate =
        mock(KafkaTemplate.class);

    private final OutboxKafkaProducer outboxKafkaProducer =
        new OutboxKafkaProducer(kafkaTemplate);

    @Test
    @DisplayName("send 성공 - KafkaTemplate.send().get() 정상 호출")
    void send_success() {
        // given
        String topic = "payment.approved";
        String key = "1";
        String payload = "{\"orderId\":1}";

        CompletableFuture<SendResult<String, String>> future =
            CompletableFuture.completedFuture(null);

        when(kafkaTemplate.send(topic, key, payload))
            .thenReturn(future);

        // when
        outboxKafkaProducer.send(
            topic,
            key,
            payload
        );

        // then
        verify(kafkaTemplate).send(
            topic,
            key,
            payload
        );
    }

    @Test
    @DisplayName("send 실패 - 예외 발생 시 RuntimeException으로 전환한다")
    void send_fail_whenKafkaSendFails() {
        // given
        String topic = "payment.approved";
        String key = "1";
        String payload = "{\"orderId\":1}";

        CompletableFuture<SendResult<String, String>> future =
            new CompletableFuture<>();

        future.completeExceptionally(new RuntimeException("kafka error"));

        when(kafkaTemplate.send(topic, key, payload))
            .thenReturn(future);

        // when & then
        assertThatThrownBy(() -> outboxKafkaProducer.send(
            topic,
            key,
            payload
        ))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Kafka 발행 실패");

        verify(kafkaTemplate).send(
            topic,
            key,
            payload
        );
    }

}
