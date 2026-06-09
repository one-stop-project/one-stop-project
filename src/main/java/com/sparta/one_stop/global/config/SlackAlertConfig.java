package com.sparta.one_stop.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SlackAlertConfig {

    /**
     * Slack Webhook 호출 전용 RestClient Bean
     * - SlackAlertService에서 @Qualifier("slackRestClient")로 주입받아 사용한다.
     * - RestClient를 Service 내부에서 직접 생성하지 않고 Bean으로 분리하여 테스트 시 mock으로 교체할 수 있게 한다.
     * - 다른 외부 API용 RestClient Bean이 추가되어도 Slack 전용 Bean을 명확히 구분할 수 있다.
     */
    @Bean
    public RestClient slackRestClient() {
        return RestClient.create();
    }

}
