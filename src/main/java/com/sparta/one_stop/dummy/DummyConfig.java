package com.sparta.one_stop.dummy;

import com.sparta.one_stop.dummy.description.DummyPromptProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// 더미 시드 도구 공통 설정 — 외부화 프롬프트 properties 활성화 (NaverApiConfig와 달리 무조건 로드)
@Configuration
@EnableConfigurationProperties(DummyPromptProperties.class)
public class DummyConfig {
}
