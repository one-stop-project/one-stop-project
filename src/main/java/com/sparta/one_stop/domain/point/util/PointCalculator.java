package com.sparta.one_stop.domain.point.util;

import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PointCalculator {

    public static final BigDecimal EARN_RATE = new BigDecimal("0.01");

    // 최대 적립한도 1억(test용)
    private static final int SAFE_MAX_AMOUNT = 100_000_000; // 1억 (실용 상한)


    // 적립 포인트 계산
    // 적립 기준 금액 = 상품금액 - 쿠폰할인 - 사용 포인트
    // 적립포인트 = floor(적립 기준 금액 * 1%)

    // @param baseAmount 적립 기준 금액( >= 0)
    // @return 적립 포인트 (소수점 버림)
    // @throws CustomException 음소 또는 비정상 값
    public static int calculateEarnedPoint(int baseAmount) {
        return calculateEarnedPoint(baseAmount, EARN_RATE);
    }

    // 적립 포인트 계산(커스텀 적립률)
    // 이벤트성 부스팅(예 : 2배 적립)이나 등급별 적릴률 적용 시 사용
    // @param baseAmount : 적립 기준 금액
    // @param rate : 적립률 (예: 0.01, 0.025, 0.05)
    // @return 적립 포인트(소수점 버림)
    public static int calculateEarnedPoint(int baseAmount, BigDecimal rate) {
        validateBaseAmount(baseAmount);
        validateRate(rate);

        if (baseAmount == 0) {
            return 0;
        }

        // BigDecimal로 정밀 계산 → setScale(0, FLOOR)로 소수점 버림 → intValueExact()로 안전 변환
        return BigDecimal.valueOf(baseAmount)
            .multiply(rate)
            .setScale(0, RoundingMode.FLOOR)
            .intValueExact();
    }


    // 적립 기준 금액 계산 - 정책 그대로
    // 적립 기준 금액 = 상품 금액 - 쿠폰 할인 금액 - 사용포인트
    // 배송비는 적립 대상에서 제외

    // productAmount : 상품 금액
    // couponDiscount : 쿠폰 할인 금액
    // usedPoint : 사용 포인트
    // return 적립 기준 금액 : 음수면 0으로 clamp

    public static int calculateEarnBase(int productAmount, int couponDiscount, int usedPoint) {
        validateBaseAmount(productAmount);
        if (couponDiscount < 0) {
            throw new CustomException(ErrorCode.COUPON_008, "쿠폰 할인 금액은 음수일 수 없습니다");
        }
        if (usedPoint < 0) {
            throw new CustomException(ErrorCode.POINT_005, "사용 포인트는 음수일 수 없습니다");
        }

        long base = (long) productAmount - couponDiscount - usedPoint;
        return Math.max(0, (int) Math.min(Integer.MAX_VALUE, base));
    }


    //----------------------검증-----------------------
    private static void validateBaseAmount(int amount) {
        if (amount < 0) {
            throw new CustomException(ErrorCode.POINT_003, "포인트 금액은 1이상 이어야 합니다.");
        }

        if (amount > SAFE_MAX_AMOUNT) {
            throw new CustomException(ErrorCode.POINT_009, "안정 상한 초과 :");
        }
    }

    private static void validateRate(BigDecimal rate) {
        if (rate == null) {
            throw new CustomException(ErrorCode.POINT_006, "적립률은 null일 수 없습니다");
        }
        if (rate.signum() < 0) {
            throw new CustomException(ErrorCode.POINT_007, "적립률은 음수일 수 없습니다: " + rate);
        }
        if (rate.compareTo(BigDecimal.ONE) > 0) {
            // 100% 초과 적립은 비정상
            throw new CustomException(ErrorCode.POINT_008, "적립률은 100%를 초과할 수 없습니다: " + rate);
        }
    }


}
