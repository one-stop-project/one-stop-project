package com.sparta.one_stop.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.one_stop.global.ai.prompt.AiPromptProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(AiPromptProperties.class)
public class AiConfig {

    // 메인 AI (eunjiom 키) — AI 리뷰 요약 · 어시스턴트 · 연관상품 추천
    // RestClientCustomizer는 Spring AI 내부 RestClient에 미적용 → OpenAiApi 직접 생성하여 인터셉터 주입
    @Bean
    @Primary
    public ChatClient mainChatClient(
        @Value("${spring.ai.openai.api-key}") String apiKey,
        @Value("${spring.ai.openai.base-url}") String baseUrl,
        @Value("${spring.ai.openai.chat.completions-path:/chat/completions}") String completionsPath,
        @Value("${spring.ai.openai.chat.options.model:gemini-3.1-flash-lite}") String model,
        ObjectMapper objectMapper
    ) {
        RestClient.Builder restClientBuilder = RestClient.builder()
            .requestInterceptor((request, body, execution) -> {
                if (body.length > 0) {
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        if (root instanceof ObjectNode node && !node.has("thinking")) {
                            node.set("thinking",
                                objectMapper.createObjectNode().put("type", "disabled"));
                            body = objectMapper.writeValueAsBytes(root);
                        }
                    } catch (Exception e) {
                        log.warn("[Gemini] thinking 비활성화 주입 실패 — error={}", e.getMessage());
                    }
                }
                return execution.execute(request, body);
            });

        OpenAiApi api = OpenAiApi.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .completionsPath(completionsPath)
            .restClientBuilder(restClientBuilder)
            .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder().model(model).build())
            .build();

        return ChatClient.builder(chatModel).build();
    }

    // 더미 AI (junghyun 키, S2는 GEMINI_API_KEY fallback) — 상품 더미데이터 생성 전용
    @Bean
    public ChatClient dummyChatClient(
        @Value("${gemini.dummy-api-key}") String dummyApiKey,
        @Value("${spring.ai.openai.base-url}") String baseUrl,
        @Value("${spring.ai.openai.chat.completions-path:/chat/completions}") String completionsPath,
        @Value("${spring.ai.openai.chat.options.model:gemini-2.5-flash-lite}") String model
    ) {
        OpenAiApi api = OpenAiApi.builder()
            .apiKey(dummyApiKey)
            .baseUrl(baseUrl)
            .completionsPath(completionsPath)
            .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiApi(api)
            .defaultOptions(OpenAiChatOptions.builder().model(model).build())
            .build();
        return ChatClient.builder(chatModel).build();
    }
}
