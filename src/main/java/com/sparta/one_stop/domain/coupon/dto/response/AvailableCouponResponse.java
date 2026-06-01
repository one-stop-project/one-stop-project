package com.sparta.one_stop.domain.coupon.dto.response;

import com.sparta.one_stop.global.enums.coupon.CouponDiscountType;

import java.time.LocalDateTime;

public record AvailableCouponResponse(

    Long couponId,

    String name,

    CouponDiscountType discountType,

    Integer discountValue,

    Long minOrderPrice,

    Long maxDiscountPrice,

    Integer remainingQuantity,

    LocalDateTime startAt,

    LocalDateTime expiredAt

) {
}
