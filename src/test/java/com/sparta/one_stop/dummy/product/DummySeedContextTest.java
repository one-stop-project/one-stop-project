package com.sparta.one_stop.dummy.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// 부팅 대신 컨텍스트 로드로 검증 — 네이버 키 + 스케줄러 플래그 ON일 때
// 조건부 시드 빈(오케스트레이터·스케줄러)이 의존성까지 전부 배선되는지 확인. (외부 호출은 일어나지 않음)
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "naver.api.client-id=test-id",
        "naver.api.client-secret=test-secret",
        "naver.api.base-url=https://openapi.naver.com/v1/search/shop.json",
        "dummy.scheduler.enabled=true"
})
@DisplayName("더미 시드 빈 배선 (컨텍스트 로드)")
class DummySeedContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("네이버 키 + 스케줄러 플래그 ON → 오케스트레이터·스케줄러·writer 빈 생성")
    void seedBeansWired() {
        assertThat(context.getBeansOfType(DummyProductOrchestrator.class)).isNotEmpty();
        assertThat(context.getBeansOfType(DummyProductScheduler.class)).isNotEmpty();
        assertThat(context.getBeansOfType(DummyProductWriter.class)).isNotEmpty();
    }
}
