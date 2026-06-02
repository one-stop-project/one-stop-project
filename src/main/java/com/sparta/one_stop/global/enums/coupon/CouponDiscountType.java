package com.sparta.one_stop.global.enums.coupon;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CouponDiscountType {

    RATE("정률"),
    FIXED("정액");

    private final String description;

}
