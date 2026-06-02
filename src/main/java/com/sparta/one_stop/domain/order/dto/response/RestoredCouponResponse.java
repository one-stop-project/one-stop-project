package com.sparta.one_stop.domain.order.dto.response;

import com.sparta.one_stop.domain.coupon.dto.CouponRestoreResult;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;

public record RestoredCouponResponse(

    Long userCouponId,

    String couponName,

    UserCouponStatus status

) {

    // 주문 취소 응답에 포함할 쿠폰 복구 정보 생성
    // 쿠폰 도메인의 복구 결과 DTO를 주문 응답 DTO로 변환한다
    public static RestoredCouponResponse of(CouponRestoreResult result) {
        if (result == null) {
            return null;
        }

        return new RestoredCouponResponse(
            result.userCouponId(),
            result.couponName(),
            result.status()
        );
    }

}
