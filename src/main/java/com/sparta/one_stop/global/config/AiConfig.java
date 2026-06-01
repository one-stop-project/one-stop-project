package com.sparta.one_stop.global.config;

import com.sparta.one_stop.global.ai.prompt.AiPromptProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiPromptProperties.class)
public class AiConfig {

    /**
     * Spring AI ChatClient 빈 등록.
     * Builder 는 Spring AI 자동 설정이 주입하며, 내부적으로 OpenAI ChatModel 을 사용합니다.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
