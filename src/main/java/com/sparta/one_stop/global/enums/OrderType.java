package com.sparta.one_stop.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderType {

    DIRECT("바로 구매"),
    CART("장바구니 구매");

    private final String description;

}
