package com.sparta.one_stop.domain.review.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ProductReviewResponse(

    Long reviewId,
    String reviewerName,
    Integer rating,
    String content,
    List<String> imageUrls,
    LocalDateTime createdAt
) {
}
