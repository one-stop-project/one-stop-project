package com.sparta.one_stop.global.ai.prompt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * prompts.yml 에 선언된 ai.prompts.* 값을 바인딩합니다.
 * 프롬프트를 수정할 때는 prompts.yml 만 교체하면 되며,
 * 운영 환경에서는 spring.config.location 으로 외부 파일을 지정하면 재배포 없이 적용 가능합니다.
 */
@ConfigurationProperties(prefix = "ai.prompts")
public record AiPromptProperties(
        String clothing,
        String electronics,
        String food,
        String general,
        String assistant,
        String incremental
) {}
