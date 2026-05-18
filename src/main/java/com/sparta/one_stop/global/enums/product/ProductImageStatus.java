package com.sparta.one_stop.global.enums.product;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductImageStatus {

    ACTIVE("활성"),
    DELETED("삭제됨");

    private final String description;

    public boolean isActive() {
        return this == ACTIVE;
    }
}
