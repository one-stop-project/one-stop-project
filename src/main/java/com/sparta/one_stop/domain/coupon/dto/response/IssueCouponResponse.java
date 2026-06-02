package com.sparta.one_stop.domain.coupon.dto.response;

import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
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

    // 쿠폰 발급 응답 생성
    // 발급된 UserCoupon 기준으로 userCouponId, 발급 상태, 발급일을 응답한다
    public static IssueCouponResponse of(UserCoupon userCoupon) {
        Coupon coupon = userCoupon.getCoupon();

        return new IssueCouponResponse(
            userCoupon.getId(),
            coupon.getId(),
            coupon.getName(),
            userCoupon.getStatus(),
            userCoupon.getCreatedAt(),
            coupon.getExpiredAt()
        );
    }

}
