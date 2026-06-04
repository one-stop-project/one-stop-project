package com.sparta.one_stop.global.outbox.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxKafkaProducer {

    private static final int SEND_TIMEOUT_SECONDS = 3;

    private final KafkaTemplate<String, String> kafkaTemplate;

    // OutboxEvent에 저장된 topic, partitionKey, payload를 Kafka 메시지로 동기 발행한다
    public void send(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload)
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.debug("Kafka 발행 성공 - topic: {}, key: {}", topic, key);
        } catch (Exception e) {
            log.debug("Kafka 발행 실패 - topic: {}, key: {}", topic, key, e);

            throw new RuntimeException("Kafka 발행 실패: " + e.getMessage(), e);
        }
    }

}
