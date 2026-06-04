package com.sparta.one_stop.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private static final String PAYMENT_APPROVED_TOPIC = "payment.approved";

    // 결제 승인 이벤트 Topic 생성
    @Bean
    public NewTopic paymentApprovedTopic() {
        return TopicBuilder.name(PAYMENT_APPROVED_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }

}
