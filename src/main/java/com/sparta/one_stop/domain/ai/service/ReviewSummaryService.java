package com.sparta.one_stop.domain.ai.service;

import com.sparta.one_stop.domain.ai.dto.ReviewCategoryType;
import com.sparta.one_stop.domain.ai.dto.ReviewSummary;
import com.sparta.one_stop.global.ai.fallback.AiFallbackHandler;
import com.sparta.one_stop.global.ai.logging.AiTokenLogger;
import com.sparta.one_stop.global.ai.prompt.AiPromptProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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

    // fallback 로직을 AiFallbackHandler 로 위임 – 구현체 교체가 쉽습니다
    private final AiFallbackHandler<ReviewSummary> fallbackHandler;

    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String modelName;

    public ReviewSummaryService(ChatClient chatClient,
                                AiPromptProperties promptProperties,
                                AiTokenLogger tokenLogger) {
        this.chatClient = chatClient;
        this.promptProperties = promptProperties;
        this.tokenLogger = tokenLogger;
        // 기본 fallback: 빈 요약 반환
        this.fallbackHandler = throwable -> ReviewSummary.unavailable();
    }

    /**
     * 카테고리별 프롬프트로 리뷰를 AI 에 전송하고 구조화된 요약을 반환합니다.
     * Circuit Breaker "ai-review" 인스턴스로 보호됩니다.
     *
     * @param category 카테고리 타입 (의류/전자제품/식품/기타)
     * @param reviews  줄바꿈으로 구분된 리뷰 목록 문자열
     */
    @CircuitBreaker(name = "ai-review", fallbackMethod = "summarizeFallback")
    public ReviewSummary summarize(ReviewCategoryType category, String reviews) {
        String requestId = UUID.randomUUID().toString();

        // BeanOutputConverter: ReviewSummary 필드 기반 JSON 스키마 생성 및 응답 파싱
        BeanOutputConverter<ReviewSummary> converter = new BeanOutputConverter<>(ReviewSummary.class);

        // 카테고리 프롬프트 + {reviews} 치환 + JSON 스키마 지시문 조합
        String userMessage = buildUserMessage(category, reviews, converter.getFormat());

        long start = System.currentTimeMillis();
        try {
            ChatResponse response = chatClient.prompt()
                    .user(userMessage)
                    .call()
                    .chatResponse();

            tokenLogger.logSuccess(requestId, response, System.currentTimeMillis() - start);

            String content = response.getResult().getOutput().getText();
            return converter.convert(content);

        } catch (Exception e) {
            tokenLogger.logFailure(requestId, System.currentTimeMillis() - start, modelName, e);
            throw e;
        }
    }

    /**
     * Resilience4j CircuitBreaker fallback 메서드.
     * 시그니처는 원본 메서드 + Throwable 파라미터여야 합니다.
     */
    ReviewSummary summarizeFallback(ReviewCategoryType category, String reviews, Throwable t) {
        log.warn("[AI Fallback] ai-review circuit breaker triggered. category={} reason={}",
                category, t.getMessage());
        return fallbackHandler.handle(t);
    }

    private String buildUserMessage(ReviewCategoryType category, String reviews, String formatInstructions) {
        String template = switch (category) {
            case CLOTHING -> promptProperties.clothing();
            case ELECTRONICS -> promptProperties.electronics();
            case FOOD -> promptProperties.food();
            case GENERAL -> promptProperties.general();
        };

        // {reviews} 플레이스홀더 치환 후 포맷 지시문 추가
        return template.replace("{reviews}", reviews)
                + "\n\n응답 형식 지침:\n" + formatInstructions;
    }
}
