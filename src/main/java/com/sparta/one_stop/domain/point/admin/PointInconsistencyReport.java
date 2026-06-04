package com.sparta.one_stop.domain.point.admin;

import lombok.Builder;

@Builder
public record PointInconsistencyReport(
    Long userId,
    Integer balance,
    Integer remainingAmountSum,
    Integer diff
) {
}
