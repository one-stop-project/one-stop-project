package com.sparta.one_stop.domain.product.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PopularKeywordResponse {

    private int rank;
    private String keyword;
    private long count;

    public static PopularKeywordResponse of(int rank, String keyword, double score) {
        return PopularKeywordResponse.builder()
            .rank(rank)
            .keyword(keyword)
            .count(Math.round(score))
            .build();
    }
}
