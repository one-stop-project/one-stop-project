package com.sparta.one_stop.domain.product.event;

import java.time.LocalDateTime;

public record SearchHistoryEvent(
    String keyword,
    Long userId,
    LocalDateTime searchedAt
) {
}
