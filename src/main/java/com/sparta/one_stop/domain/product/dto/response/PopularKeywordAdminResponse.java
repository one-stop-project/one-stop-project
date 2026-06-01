package com.sparta.one_stop.domain.product.dto.response;

import java.util.List;

public record PopularKeywordAdminResponse(
    String date,
    List<PopularKeywordResponse> keywords
) {
}
