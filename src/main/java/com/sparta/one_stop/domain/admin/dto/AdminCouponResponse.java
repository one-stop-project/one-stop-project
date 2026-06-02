package com.sparta.one_stop.domain.admin.dto;

import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.global.enums.coupon.CouponDiscountType;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;

import java.time.LocalDateTime;

public record AdminCouponResponse(

    Long couponId,
    String name,
    CouponDiscountType discountType,
    Integer discountValue,
    Long minOrderPrice,
    Long maxDiscountPrice,
    Integer totalQuantity,
    Integer issuedQuantity,
    Integer remainingQuantity,
    CouponStatus status,
    LocalDateTime startAt,
    LocalDateTime expiredAt,
    LocalDateTime createdAt
) {

    public static AdminCouponResponse from(Coupon coupon) {
        return new AdminCouponResponse(
            coupon.getId(),
            coupon.getName(),
            coupon.getDiscountType(),
            coupon.getDiscountValue(),
            coupon.getMinOrderPrice(),
            coupon.getMaxDiscountPrice(),
            coupon.getTotalQuantity(),
            coupon.getIssuedQuantity(),
            coupon.getTotalQuantity() - coupon.getIssuedQuantity(),
            coupon.getStatus(),
            coupon.getStartAt(),
            coupon.getExpiredAt(),
            coupon.getCreatedAt()
        );
    }
}
