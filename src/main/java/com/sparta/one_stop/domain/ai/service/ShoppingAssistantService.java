package com.sparta.one_stop.domain.ai.service;

import com.sparta.one_stop.domain.ai.dto.ShoppingAssistantResponse;
import com.sparta.one_stop.domain.ai.tool.ShoppingAssistantTool;
import com.sparta.one_stop.global.ai.logging.AiTokenLogger;
import com.sparta.one_stop.global.ai.prompt.AiPromptProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ShoppingAssistantService {

    private final ChatClient chatClient;
    private final AiPromptProperties promptProperties;
    private final AiTokenLogger tokenLogger;
    private final ShoppingAssistantTool shoppingAssistantTool;

    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String modelName;

    public ShoppingAssistantService(ChatClient chatClient,
                                    AiPromptProperties promptProperties,
                                    AiTokenLogger tokenLogger,
                                    ShoppingAssistantTool shoppingAssistantTool) {
        this.chatClient = chatClient;
        this.promptProperties = promptProperties;
        this.tokenLogger = tokenLogger;
        this.shoppingAssistantTool = shoppingAssistantTool;
    }

    @PostConstruct
    void validatePrompts() {
        if (promptProperties.assistant() == null) {
            throw new IllegalStateException("ai.prompts.assistant 설정이 누락되었습니다.");
        }
    }

    @CircuitBreaker(name = "ai-assistant", fallbackMethod = "assistFallback")
    public ShoppingAssistantResponse assist(String userMessage, Long categoryId) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        MDC.put(AiTokenLogger.MDC_REQUEST_ID, requestId);
        try {
            ChatResponse response = chatClient.prompt()
                .system(buildSystemPrompt(categoryId))
                .user(userMessage)
                .tools(shoppingAssistantTool)
                .options(OpenAiChatOptions.builder()
                    .additionalOptions(Map.of("thinking", Map.of("type", "disabled")))
                    .build())
                .call()
                .chatResponse();

            if (response == null || response.getResult() == null) {
                throw new IllegalStateException("AI 응답이 비어있습니다.");
            }

            tokenLogger.logSuccess(requestId, response, System.currentTimeMillis() - start);
            return new ShoppingAssistantResponse(response.getResult().getOutput().getText());

        } catch (Exception e) {
            tokenLogger.logFailure(requestId, System.currentTimeMillis() - start, modelName, e);
            throw e;
        } finally {
            MDC.remove(AiTokenLogger.MDC_REQUEST_ID);
        }
    }

    private String buildSystemPrompt(Long categoryId) {
        String base = promptProperties.assistant();
        if (categoryId == null) return base;
        return base + "\n[카테고리 힌트] 현재 고객이 카테고리 ID " + categoryId
            + " 페이지를 보고 있습니다. 질문이 이 카테고리와 관련 있으면 searchProducts의 categoryId 파라미터에 "
            + categoryId + "를 사용하고, 관련 없으면 null로 전체 검색하세요.";
    }

    ShoppingAssistantResponse assistFallback(String userMessage, Long categoryId, Throwable t) {
        log.warn("[AI Assistant] circuit breaker triggered. reason={}", t.getMessage());
        return new ShoppingAssistantResponse("현재 AI 어시스턴트를 이용할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }
}
