package com.sparta.one_stop.dummy;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 더미 시드 동작 파라미터 (dummy.seed.*). 미설정 시 안전 기본값으로 보정.
@ConfigurationProperties(prefix = "dummy.seed")
public record DummySeedProperties(
        int display,            // 키워드당 네이버 검색 건수 (1~100)
        long throttleMs,        // 카테고리 사이 대기(ms) — LLM 분당 한도(RPM) 보호
        String defaultImageUrl  // 이미지 누락/다운로드 실패 시 대체 URL
) {
    public DummySeedProperties {
        if (display <= 0 || display > 100) {
            display = 100;
        }
        if (throttleMs < 0) {
            throttleMs = 4000L;
        }
        if (defaultImageUrl == null || defaultImageUrl.isBlank()) {
            defaultImageUrl = "/images/default-product.png";
        }
    }
}
