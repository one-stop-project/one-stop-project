package com.sparta.one_stop.domain.review.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ReviewResponse(
    Long reviewId,
    Long productId,
    Long orderItemId,
    Integer rating,
    String content,
    List<String> images,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
