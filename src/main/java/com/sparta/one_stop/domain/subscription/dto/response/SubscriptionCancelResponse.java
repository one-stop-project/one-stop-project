package com.sparta.one_stop.domain.subscription.dto.response;

import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;

import java.time.LocalDateTime;

public record SubscriptionCancelResponse(
    Long subscriptionId,
    SubscriptionStatus status,
    LocalDateTime endAt,
    String message
) {
}
