package com.sparta.one_stop.domain.ai.service;

import com.sparta.one_stop.domain.ai.dto.ReviewCategoryType;
import com.sparta.one_stop.domain.ai.dto.ReviewSummary;
import com.sparta.one_stop.global.ai.logging.AiTokenLogger;
import com.sparta.one_stop.global.ai.prompt.AiPromptProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewSummaryService Fallback 테스트")
class ReviewSummaryFallbackTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private AiPromptProperties promptProperties;

    @Mock
    private AiTokenLogger tokenLogger;

    private ReviewSummaryService service;

    @BeforeEach
    void setUp() {
        service = new ReviewSummaryService(chatClient, promptProperties, tokenLogger);
        ReflectionTestUtils.setField(service, "modelName", "gpt-4o-mini");
    }

    @Test
    @DisplayName("fallback 메서드는 UNAVAILABLE 상태의 ReviewSummary 를 반환한다")
    void fallback_returns_unavailable_review_summary() {
        // given
        RuntimeException cause = new RuntimeException("AI 서비스 장애");

        // when - fallback 메서드를 직접 호출해 동작을 검증합니다
        ReviewSummary result = service.summarizeFallback(
                ReviewCategoryType.CLOTHING, "리뷰 내용", cause);

        // then
        assertThat(result).isNotNull();
        assertThat(result.sentiment()).isEqualTo("UNAVAILABLE");
        assertThat(result.pros()).isEmpty();
        assertThat(result.cons()).isEmpty();
        assertThat(result.keywords()).isEmpty();
    }

    @Test
    @DisplayName("CallNotPermittedException(Circuit OPEN) 에도 fallback 이 UNAVAILABLE 을 반환한다")
    void fallback_on_circuit_open_exception() {
        // given – Circuit Breaker 가 OPEN 상태일 때 Resilience4j 가 던지는 예외 타입
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        CircuitBreaker cb = registry.circuitBreaker("test-cb",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(1)
                        .failureRateThreshold(100)
                        .build());

        // 강제로 OPEN 전환
        cb.transitionToOpenState();
        CallNotPermittedException openException = CallNotPermittedException.createCallNotPermittedException(cb);

        // when
        ReviewSummary result = service.summarizeFallback(
                ReviewCategoryType.ELECTRONICS, "리뷰 내용", openException);

        // then
        assertThat(result.sentiment()).isEqualTo("UNAVAILABLE");
    }

    @Test
    @DisplayName("모든 카테고리 타입에서 fallback 이 동일하게 UNAVAILABLE 을 반환한다")
    void fallback_works_for_all_category_types() {
        RuntimeException cause = new RuntimeException("타임아웃");

        for (ReviewCategoryType type : ReviewCategoryType.values()) {
            ReviewSummary result = service.summarizeFallback(type, "리뷰", cause);
            assertThat(result.sentiment())
                    .as("카테고리 %s 의 fallback 은 UNAVAILABLE 이어야 합니다", type)
                    .isEqualTo("UNAVAILABLE");
        }
    }
}
