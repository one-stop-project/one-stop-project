package com.sparta.one_stop.domain.ai.dto;

import java.util.List;
import java.util.Set;

/**
 * AI 리뷰 요약 응답 구조체.
 * Spring AI BeanOutputConverter 가 이 record 의 필드 정보로 JSON 스키마를 생성합니다.
 *
 * sentiment 허용값: POSITIVE / NEUTRAL / NEGATIVE / UNAVAILABLE
 */
public record ReviewSummary(
        List<String> pros,
        List<String> cons,
        List<String> keywords,
        String sentiment
) {

    private static final Set<String> VALID_SENTIMENTS = Set.of("POSITIVE", "NEUTRAL", "NEGATIVE");

    /** Circuit Breaker fallback 또는 AI 오류 시 반환하는 기본값 */
    public static ReviewSummary unavailable() {
        return new ReviewSummary(
                List.of(),
                List.of(),
                List.of(),
                "UNAVAILABLE"
        );
    }

    public boolean isUnavailable() {
        return sentiment == null || !VALID_SENTIMENTS.contains(sentiment);
    }
}
