package com.sparta.one_stop.global.enums.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductItemStatus {

    ON_SALE("판매중"),
    STOP("판매중단");

    private final String description;

    public boolean isOnSale() {
        return this == ON_SALE;
    }
}
