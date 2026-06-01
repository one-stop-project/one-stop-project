package com.sparta.one_stop.domain.point.dto.response;

import java.util.List;

public record PointHistoryPageResponse(

    List<PointHistoryResponse> content,

    int page,

    int size,

    long totalElements,

    int totalPages

) {
}
