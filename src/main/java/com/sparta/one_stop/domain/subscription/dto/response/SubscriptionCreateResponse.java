package com.sparta.one_stop.domain.subscription.dto.response;

import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;

import java.time.LocalDateTime;

public record SubscriptionCreateResponse(
    Long subscriptionId,
    LocalDateTime startAt,
    LocalDateTime endAt,
    LocalDateTime nextPaymentDate,
    SubscriptionStatus status
) {
}
