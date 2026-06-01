package com.sparta.one_stop.global.enums.coupon;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CouponStatus {

    ACTIVE("활성"),
    INACTIVE("비활성");

    private final String description;

}
