package com.sparta.one_stop.domain.coupon.dto.response;

import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
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

    // 내 쿠폰 응답 생성
    // UserCoupon.createdAt은 쿠폰 발급일로 사용한다
    // usedAt은 실제 쿠폰 사용 일시, expiredAt은 쿠폰 마스터의 만료일을 의미한다
    public static MyCouponResponse of(UserCoupon userCoupon) {
        Coupon coupon = userCoupon.getCoupon();

        return new MyCouponResponse(
            userCoupon.getId(),
            coupon.getId(),
            coupon.getName(),
            coupon.getDiscountType(),
            coupon.getDiscountValue(),
            coupon.getMinOrderPrice(),
            coupon.getMaxDiscountPrice(),
            userCoupon.getStatus(),
            userCoupon.getCreatedAt(),
            userCoupon.getUsedAt(),
            coupon.getExpiredAt()
        );
    }

}
