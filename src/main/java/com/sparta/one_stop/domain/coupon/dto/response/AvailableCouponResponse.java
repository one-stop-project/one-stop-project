package com.sparta.one_stop.domain.coupon.dto.response;

import com.sparta.one_stop.domain.coupon.entity.Coupon;
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

    // 발급 가능 쿠폰 응답 생성
    // remainingQuantity는 DB 기준 잔여 수량으로 계산한다
    // 실제 선착순 발급 수량 제어는 Redis stock key 기준으로 처리한다
    public static AvailableCouponResponse of(Coupon coupon) {
        return new AvailableCouponResponse(
            coupon.getId(),
            coupon.getName(),
            coupon.getDiscountType(),
            coupon.getDiscountValue(),
            coupon.getMinOrderPrice(),
            coupon.getMaxDiscountPrice(),
            coupon.getTotalQuantity() - coupon.getIssuedQuantity(),
            coupon.getStartAt(),
            coupon.getExpiredAt()
        );
    }

}
