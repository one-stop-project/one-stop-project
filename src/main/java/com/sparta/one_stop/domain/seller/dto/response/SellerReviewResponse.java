package com.sparta.one_stop.domain.seller.dto.response;

import java.time.LocalDateTime;

public record SellerReviewResponse(
    Long reviewId,
    Long productId,
    String productName,
    String thumbnailUrl,
    Long orderItemId,
    String buyerName,
    int rating,
    String content,
    long imageCount,
    LocalDateTime createdAt
) {
    public SellerReviewResponse(
        Long reviewId, Long productId, String productName, String thumbnailUrl,
        Long orderItemId, String buyerName, int rating, String content,
        Number imageCount, LocalDateTime createdAt
    ) {
        this(
            reviewId, productId, productName, thumbnailUrl, orderItemId, buyerName,
            rating, content, imageCount == null ? 0L : imageCount.longValue(), createdAt
        );
    }
}
