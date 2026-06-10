package com.sparta.one_stop.domain.admin.dto;

import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;

import java.time.LocalDateTime;

public record AdminSubscriptionResponse(
    Long subscriptionId,
    Long userId,
    String userEmail,
    String userName,
    SubscriptionStatus status,
    LocalDateTime startAt,
    LocalDateTime endAt,
    LocalDateTime nextPaymentDate,
    String cancelReason,
    LocalDateTime createdAt
) {
    public static AdminSubscriptionResponse from(Subscription subscription) {
        return new AdminSubscriptionResponse(
            subscription.getId(),
            subscription.getUser().getId(),
            subscription.getUser().getEmail(),
            subscription.getUser().getName(),
            subscription.getStatus(),
            subscription.getStartAt(),
            subscription.getEndAt(),
            subscription.getNextPaymentDate(),
            subscription.getCancelReason(),
            subscription.getCreatedAt()
        );
    }
}
