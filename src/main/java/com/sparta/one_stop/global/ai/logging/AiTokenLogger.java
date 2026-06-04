package com.sparta.one_stop.global.ai.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 호출 후 토큰 사용량을 MDC 와 함께 로깅합니다.
 * requestId 를 MDC 에 심어 로그 집계 도구(ELK 등)에서 요청 단위 추적이 가능합니다.
 */
@Slf4j
@Component
public class AiTokenLogger {

    public static final String MDC_REQUEST_ID = "ai.requestId";

    /**
     * 성공 응답 로깅.
     * ChatResponseMetadata 가 null 이거나 Usage 를 지원하지 않는 모델이면 토큰은 0으로 기록합니다.
     */
    public void logSuccess(String requestId, ChatResponse response, long latencyMs) {
        long promptTokens = 0L;
        long completionTokens = 0L;
        String model = "unknown";

        if (response != null && response.getMetadata() != null) {
            model = response.getMetadata().getModel() != null
                    ? response.getMetadata().getModel() : "unknown";

            var usage = response.getMetadata().getUsage();
            if (usage != null) {
                promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0L;
                completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0L;
            }
        }

        MDC.put(MDC_REQUEST_ID, requestId);
        try {
            log.info("[AI] requestId={} model={} promptTokens={} completionTokens={} latencyMs={} status=SUCCESS",
                    requestId, model, promptTokens, completionTokens, latencyMs);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    /** searchProducts 툴 호출 로깅 — 실제 사용된 categoryId를 포함해 디버깅 추적용 */
    public void logSearchCall(String keyword, List<String> tags, Long categoryId, Long maxPrice) {
        String requestId = MDC.get(MDC_REQUEST_ID);
        log.info("[AI Tool] searchProducts requestId={} keyword={} tags={} categoryId={} maxPrice={}",
                requestId != null ? requestId : "unknown",
                keyword, tags, categoryId, maxPrice);
    }

    /** 실패 응답 로깅 */
    public void logFailure(String requestId, long latencyMs, String model, Throwable t) {
        MDC.put(MDC_REQUEST_ID, requestId);
        try {
            log.error("[AI] requestId={} model={} latencyMs={} status=FAILED error={}",
                    requestId, model, latencyMs, t.getMessage());
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }
}
