package com.sparta.one_stop.global.enums.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InventoryHistoryType {

    INBOUND("입고"),
    ADJUSTMENT("재고 보정"),
    OUTBOUND("출고"),
    RESTORE("복구");

    private final String description;
}
