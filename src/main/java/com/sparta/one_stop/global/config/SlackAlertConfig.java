package com.sparta.one_stop.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class SlackAlertConfig {

    /**
     * Slack Webhook 호출 전용 RestClient Bean
     *
     * 설정 이유:
     * - Slack Webhook은 외부 네트워크 호출이다.
     * - timeout이 없으면 Slack 응답 지연 시 호출 스레드가 장시간 대기할 수 있다.
     * - Outbox DEAD 알림은 보조 알림 기능이므로, Slack 지연이 Outbox 처리 흐름에 큰 영향을 주면 안 된다.
     *
     * timeout 정책:
     * - connect timeout: 2초
     * - read timeout: 3초
     */
    @Bean
    public RestClient slackRestClient() {
        SimpleClientHttpRequestFactory requestFactory =
            new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));

        return RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }

}
