package com.sparta.one_stop.domain.ai.dto;

public record AiReviewSummaryResponse(
    long reviewCount,
    double averageRating,
    ReviewSummary summary
) {
    public static AiReviewSummaryResponse insufficient(long reviewCount) {
        return new AiReviewSummaryResponse(reviewCount, 0.0, null);
    }

    public static AiReviewSummaryResponse of(long reviewCount, double averageRating, ReviewSummary summary) {
        return new AiReviewSummaryResponse(reviewCount, averageRating, summary);
    }
}
