package com.sparta.one_stop.domain.coupon.dto.response;

import com.sparta.one_stop.global.enums.coupon.CouponDiscountType;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;

import java.time.LocalDateTime;

public record MyCouponResponse(

    Long userCouponId,

    Long couponId,

    String couponName,

    CouponDiscountType discountType,

    Integer discountValue,

    Long minOrderPrice,

    Long maxDiscountPrice,

    UserCouponStatus status,

    LocalDateTime issuedAt,

    LocalDateTime usedAt,

    LocalDateTime expiredAt

) {
}
