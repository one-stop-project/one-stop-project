package com.sparta.one_stop.domain.coupon.dto.response;

import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;

import java.time.LocalDateTime;

public record IssueCouponResponse(

    Long userCouponId,

    Long couponId,

    String couponName,

    UserCouponStatus status,

    LocalDateTime issuedAt,

    LocalDateTime expiredAt

) {
}
