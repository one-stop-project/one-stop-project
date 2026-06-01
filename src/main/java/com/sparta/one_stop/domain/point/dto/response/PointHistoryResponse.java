package com.sparta.one_stop.domain.point.dto.response;

import com.sparta.one_stop.global.enums.point.PointHistoryType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PointHistoryResponse(

    Long historyId,

    Integer amount,

    PointHistoryType type,

    String description,

    LocalDate expireAt,

    LocalDateTime createdAt

) {
}
