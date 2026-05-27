package com.sparta.one_stop.domain.review.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record MyReviewResponse(

    Long reviewId,
    Long productId,
    String productName,
    Integer rating,
    String content,
    LocalDateTime createdAt
) {
}
