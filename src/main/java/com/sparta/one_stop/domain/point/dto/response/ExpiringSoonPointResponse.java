package com.sparta.one_stop.domain.point.dto.response;

import java.time.LocalDate;

public record ExpiringSoonPointResponse(

    Integer amount,

    LocalDate expireAt

) {
}
