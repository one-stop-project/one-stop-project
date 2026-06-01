package com.sparta.one_stop.domain.point.dto.response;

import java.time.LocalDate;

public record PointChargeResponse(

    Integer balance,

    Integer earnedAmount,

    LocalDate expireAt

) {
}
