package com.sparta.one_stop.domain.admin.dto;

public record AdminSubscriptionStatsResponse(
    long activeCount,
    long cancelledCount,
    long expiredCount,
    long totalCount
) {}
