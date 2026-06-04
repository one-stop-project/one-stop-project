package com.sparta.one_stop.domain.point.admin;

import lombok.Builder;

@Builder
public record PointSystemStats(
    long totalCharged,
    long totalEarned,
    long totalUsed,
    long totalRefunded,
    long totalExpired,
    long totalLiveBalance,
    long accountingDiff,
    boolean isConsistent
) {
}
