package com.sparta.one_stop.domain.product.event;

import java.time.LocalDateTime;

// eventId = 재처리(ack 실패 후 같은 batch 재peek) 중복 방지용 멱등키.
// 검색 적재 시점(recordKeyword)에 1회 생성되어 큐 payload에 함께 직렬화된다.
public record SearchHistoryEvent(
    String eventId,
    String keyword,
    Long userId,
    LocalDateTime searchedAt
) {
}
