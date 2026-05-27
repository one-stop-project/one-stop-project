package com.sparta.one_stop.domain.review.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ReviewableOrderItemResponse(

    Long orderItemId,
    Long productId,
    String productName,
    String optionName,
    LocalDateTime deliveredAt
) {
}
