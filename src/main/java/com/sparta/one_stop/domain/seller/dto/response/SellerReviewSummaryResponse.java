package com.sparta.one_stop.domain.seller.dto.response;

public record SellerReviewSummaryResponse(
    long reviewCount,
    double averageRating,
    long rating5Count,
    long rating4Count,
    long rating3Count,
    long rating2Count,
    long rating1Count
) {
    public SellerReviewSummaryResponse(
        Number reviewCount, Number averageRating, Number rating5Count, Number rating4Count,
        Number rating3Count, Number rating2Count, Number rating1Count
    ) {
        this(
            value(reviewCount), averageRating == null ? 0.0 : Math.round(averageRating.doubleValue() * 10.0) / 10.0,
            value(rating5Count), value(rating4Count), value(rating3Count),
            value(rating2Count), value(rating1Count)
        );
    }

    private static long value(Number number) {
        return number == null ? 0L : number.longValue();
    }
}
