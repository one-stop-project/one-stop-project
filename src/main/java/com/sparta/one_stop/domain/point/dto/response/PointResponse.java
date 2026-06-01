package com.sparta.one_stop.domain.point.dto.response;

public record PointResponse(

    Integer balance,

    ExpiringSoonPointResponse expiringSoon,

    PointHistoryPageResponse history

) {
}
