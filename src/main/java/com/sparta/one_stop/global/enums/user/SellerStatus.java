package com.sparta.one_stop.global.enums.user;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SellerStatus {

    PENDING("승인대기"),
    APPROVED("승인완료"),
    REJECTED("반려"),
    SUSPENDED("정지");

    private final String description;

}
