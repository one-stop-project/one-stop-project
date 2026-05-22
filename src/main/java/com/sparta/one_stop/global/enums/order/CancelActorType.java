package com.sparta.one_stop.global.enums.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CancelActorType {

    BUYER("구매자", true),
    SELLER("판매자", true),
    ADMIN("관리자", true),
    SUPER_ADMIN("최고관리자", true),
    SYSTEM("시스템", false);

    private final String description;
    private final boolean actorIdRequired;

}
