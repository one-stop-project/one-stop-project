package com.sparta.one_stop.domain.subscription.dto.response;

import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;

import java.time.LocalDateTime;

public record SubscriptionResponse(
    Long subscriptionId,
    LocalDateTime startAt,
    LocalDateTime endAt,
    LocalDateTime nextPaymentDate,
    SubscriptionStatus status,
    String cancelReason,
    long daysLeft
) {

    public static SubscriptionResponse from(Subscription subscription) {

        long daysLeft = java.time.Duration.between(
            LocalDateTime.now(),
            subscription.getEndAt()
        ).toDays();

        return new SubscriptionResponse(
            subscription.getId(),
            subscription.getStartAt(),
            subscription.getEndAt(),
            subscription.getNextPaymentDate(),
            subscription.getStatus(),
            subscription.getCancelReason(),
            Math.max(daysLeft, 0)
        );
    }
}
