package com.sparta.one_stop.dummy.description;

import org.springframework.boot.context.properties.ConfigurationProperties;

// prompts.yml의 dummy.prompts.* 값을 바인딩 (프롬프트 외부화 — 코드 변경 없이 prompts.yml 교체로 적용)
@ConfigurationProperties(prefix = "dummy.prompts")
public record DummyPromptProperties(
        String productDescription
) {}
