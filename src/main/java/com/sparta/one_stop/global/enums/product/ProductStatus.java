package com.sparta.one_stop.global.enums.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductStatus {

    APPROVE_REQUESTED("승인 요청"),
    APPROVED("승인 완료"),
    REJECTED("반려"),
    DISCONTINUED("판매 중단"),
    FORCE_INACTIVE("강제 비활성");

    private final String description;

    public boolean isApproved() {
        return this == APPROVED;
    }

    public boolean isEditable() {
        return this == APPROVE_REQUESTED || this == APPROVED || this == REJECTED;
    }
}
