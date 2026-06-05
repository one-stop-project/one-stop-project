package com.sparta.one_stop.domain.ai.service;

import com.sparta.one_stop.domain.ai.dto.ReviewCategoryType;
import com.sparta.one_stop.domain.ai.dto.ReviewSummary;
import com.sparta.one_stop.global.ai.fallback.AiFallbackHandler;
import com.sparta.one_stop.global.ai.logging.AiTokenLogger;
import com.sparta.one_stop.global.ai.prompt.AiPromptProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class ReviewSummaryService {

    private final ChatClient chatClient;
    private final AiPromptProperties promptProperties;
    private final AiTokenLogger tokenLogger;
    private final AiFallbackHandler<ReviewSummary> fallbackHandler;

    // ReviewSummary 스키마·파서는 불변이므로 한 번만 생성 후 공유 (thread-safe)
    private final BeanOutputConverter<ReviewSummary> converter;
    private final String formatInstructions;

    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String modelName;

    public ReviewSummaryService(ChatClient chatClient,
                                AiPromptProperties promptProperties,
                                AiTokenLogger tokenLogger) {
        this.chatClient = chatClient;
        this.promptProperties = promptProperties;
        this.tokenLogger = tokenLogger;
        this.fallbackHandler = throwable -> ReviewSummary.unavailable();
        this.converter = new BeanOutputConverter<>(ReviewSummary.class);
        this.formatInstructions = this.converter.getFormat();
    }

    @PostConstruct
    void validatePrompts() {
        if (promptProperties.clothing() == null) throw new IllegalStateException("ai.prompts.clothing 설정이 누락되었습니다.");
        if (promptProperties.electronics() == null) throw new IllegalStateException("ai.prompts.electronics 설정이 누락되었습니다.");
        if (promptProperties.food() == null) throw new IllegalStateException("ai.prompts.food 설정이 누락되었습니다.");
        if (promptProperties.general() == null) throw new IllegalStateException("ai.prompts.general 설정이 누락되었습니다.");
        if (promptProperties.incremental() == null) throw new IllegalStateException("ai.prompts.incremental 설정이 누락되었습니다.");
    }

    @CircuitBreaker(name = "ai-review", fallbackMethod = "summarizeFallback")
    public ReviewSummary summarize(ReviewCategoryType category, String reviews) {
        String userMessage = buildUserMessage(category, reviews);
        return callAiAndParse(userMessage);
    }

    @CircuitBreaker(name = "ai-review", fallbackMethod = "summarizeIncrementalFallback")
    public ReviewSummary summarizeIncremental(String existingSummaryJson, String newReviews) {
        String userMessage = promptProperties.incremental()
                .replace("{existingSummary}", existingSummaryJson)
                .replace("{newReviews}", newReviews)
                + "\n\n응답 형식 지침:\n" + formatInstructions;
        return callAiAndParse(userMessage);
    }

    ReviewSummary summarizeFallback(ReviewCategoryType category, String reviews, Throwable t) {
        log.warn("[AI Fallback] ai-review circuit breaker triggered. category={} reason={}", category, t.getMessage());
        return fallbackHandler.handle(t);
    }

    ReviewSummary summarizeIncrementalFallback(String existingSummaryJson, String newReviews, Throwable t) {
        log.warn("[AI Fallback] 증분 요약 circuit breaker triggered. reason={}", t.getMessage());
        return fallbackHandler.handle(t);
    }

    private ReviewSummary callAiAndParse(String userMessage) {
        String requestId = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();
        try {
            ChatResponse response = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .chatResponse();

            if (response == null || response.getResult() == null) {
                throw new IllegalStateException("AI 응답이 비어있습니다.");
            }

            ReviewSummary result = converter.convert(response.getResult().getOutput().getText());
            tokenLogger.logSuccess(requestId, response, System.currentTimeMillis() - start);
            return result;

        } catch (Exception e) {
            tokenLogger.logFailure(requestId, System.currentTimeMillis() - start, modelName, e);
            throw e;
        }
    }

    private String buildUserMessage(ReviewCategoryType category, String reviews) {
        String template = switch (category) {
            case CLOTHING -> promptProperties.clothing();
            case ELECTRONICS -> promptProperties.electronics();
            case FOOD -> promptProperties.food();
            case GENERAL -> promptProperties.general();
        };
        return template.replace("{reviews}", reviews) + "\n\n응답 형식 지침:\n" + formatInstructions;
    }
}
