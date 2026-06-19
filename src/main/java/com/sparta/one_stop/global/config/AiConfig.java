package com.sparta.one_stop.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sparta.one_stop.global.ai.prompt.AiPromptProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(AiPromptProperties.class)
public class AiConfig {

    // gemini-3.1-flash-lite thinking 기본 활성화 → Spring AI 1.1.0이 tool call 재전송 시
    // thought_signature 미포함 → Gemini HTTP 400. 인터셉터로 thinking: disabled 주입
    @Bean
    public RestClientCustomizer geminiThinkingDisabler(ObjectMapper objectMapper) {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            if (body.length > 0 && request.getURI().toString().contains("generativelanguage")) {
                try {
                    JsonNode root = objectMapper.readTree(body);
                    if (root instanceof ObjectNode node && !node.has("thinking")) {
                        node.set("thinking",
                            objectMapper.createObjectNode().put("type", "disabled"));
                        body = objectMapper.writeValueAsBytes(root);
                    }
                } catch (Exception ignored) {
                }
            }
            return execution.execute(request, body);
        });
    }

    // 메인 AI (eunjiom 키) — AI 리뷰 요약 · 어시스턴트 · 연관상품 추천
    @Bean
    @Primary
    public ChatClient mainChatClient(ChatClient.Builder builder) {
        return builder.build();
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
