package com.sparta.one_stop.domain.coupon.dto;

import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;

public record CouponRestoreResult(

    Long userCouponId,

    String couponName,

    UserCouponStatus status

) {
}
