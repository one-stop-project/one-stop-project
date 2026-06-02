package com.sparta.one_stop.domain.coupon.dto;

import com.sparta.one_stop.domain.coupon.entity.UserCoupon;

public record CouponDiscountResult(

    UserCoupon userCoupon,

    Long discountPrice

) {

    public static CouponDiscountResult none() {
        return new CouponDiscountResult(
            null,
            0L
        );
    }

}
