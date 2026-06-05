package com.sparta.one_stop.domain.ai.dto;

public record AiReviewSummaryResponse(
    long reviewCount,
    double averageRating,
    ReviewSummary summary,
    SummaryStatus status
) {
    public enum SummaryStatus {
        /** 요약 정상 제공 */
        READY,
        /** 리뷰는 충분하나 요약 생성 중 (다음 리뷰 이벤트 후 자동 생성) */
        PENDING,
        /** 리뷰 수 부족 (MIN_REVIEW_COUNT 미달) */
        INSUFFICIENT
    }

    public static AiReviewSummaryResponse ready(long reviewCount, double averageRating, ReviewSummary summary) {
        return new AiReviewSummaryResponse(reviewCount, averageRating, summary, SummaryStatus.READY);
    }

    public static AiReviewSummaryResponse pending(long reviewCount, double averageRating) {
        return new AiReviewSummaryResponse(reviewCount, averageRating, null, SummaryStatus.PENDING);
    }

    public static AiReviewSummaryResponse insufficient(long reviewCount) {
        return new AiReviewSummaryResponse(reviewCount, 0.0, null, SummaryStatus.INSUFFICIENT);
    }
}
