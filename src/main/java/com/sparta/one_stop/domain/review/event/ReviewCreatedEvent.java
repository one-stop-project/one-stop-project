package com.sparta.one_stop.domain.review.event;

public record ReviewCreatedEvent(
    Long productId,
    Long reviewId
) {}
