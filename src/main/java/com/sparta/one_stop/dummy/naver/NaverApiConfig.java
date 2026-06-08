package com.sparta.one_stop.dummy.naver;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// 더미 데이터 시드용 네이버 RestClient 빈
// naver.api.client-id 설정된 환경(로컬 개발)에서만 활성화
// 운영/테스트엔 naver.api 설정 없어 이 설정·빈 로드 안 됨
@Configuration
@EnableConfigurationProperties(NaverApiProperties.class)
@ConditionalOnProperty(prefix = "naver.api", name = "client-id")
public class NaverApiConfig {

    @Bean
    public RestClient naverRestClient(NaverApiProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-Naver-Client-Id", properties.getClientId())
                .defaultHeader("X-Naver-Client-Secret", properties.getClientSecret())
                .build();
    }

    @Bean
    public NaverShopClient naverShopClient(RestClient naverRestClient) {
        return new NaverShopClient(naverRestClient);
    }
}
