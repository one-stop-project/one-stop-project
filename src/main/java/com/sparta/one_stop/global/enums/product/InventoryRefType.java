package com.sparta.one_stop.global.enums.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InventoryRefType {

    ORDER("주문"),
    RETURN("반품"),
    MANUAL("수동 처리");

    private final String description;
}
