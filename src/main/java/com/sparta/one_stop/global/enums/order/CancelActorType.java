package com.sparta.one_stop.global.enums.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CancelActorType {

    BUYER("구매자"),
    SELLER("판매자"),
    ADMIN("관리자"),
    SUPER_ADMIN("최고관리자"),
    SYSTEM("시스템");

    private final String description;

}
