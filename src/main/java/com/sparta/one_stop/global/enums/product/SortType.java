package com.sparta.one_stop.global.enums.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SortType {

    LATEST("최신순"),
    PRICE_ASC("가격 낮은 순"),
    PRICE_DESC("가격 높은 순"),
    POPULAR("인기순");

    private final String description;
}
