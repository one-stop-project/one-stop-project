package com.sparta.one_stop.global.outbox.kafka;

import com.sparta.one_stop.global.outbox.exception.KafkaPublishException;
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

    // OutboxEvent에 저장된 topic, partitionKey, payload를 Kafka 메시지로 동기 발행한다.
    // 발행 실패 시 Outbox Publisher가 재시도/DEAD 전이를 처리할 수 있도록 예외를 전파한다.
    public void send(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload)
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.debug("Kafka 발행 성공 - topic: {}, key: {}", topic, key);
        } catch (Exception e) {
            log.warn("Kafka 발행 실패 - topic: {}, key: {}", topic, key, e);

            throw new KafkaPublishException(
                "Kafka 발행 실패 - topic: " + topic + ", key: " + key,
                e
            );
        }
    }

}
