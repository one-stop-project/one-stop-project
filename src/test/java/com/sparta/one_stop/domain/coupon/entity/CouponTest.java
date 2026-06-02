package com.sparta.one_stop.domain.coupon.entity;

import com.sparta.one_stop.global.enums.coupon.CouponDiscountType;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponTest {

    @Test
    @DisplayName("Coupon 생성 성공 - 정액 쿠폰을 생성하면 ACTIVE 상태와 issuedQuantity 0으로 초기화된다")
    void createCoupon_success_fixedCoupon() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when
        Coupon coupon = new Coupon(
            "테스트 1000원 할인 쿠폰",
            CouponDiscountType.FIXED,
            1000,
            0L,
            null,
            100,
            now.minusDays(1),
            now.plusDays(30)
        );

        // then
        assertThat(coupon.getName()).isEqualTo("테스트 1000원 할인 쿠폰");
        assertThat(coupon.getDiscountType()).isEqualTo(CouponDiscountType.FIXED);
        assertThat(coupon.getDiscountValue()).isEqualTo(1000);
        assertThat(coupon.getMinOrderPrice()).isEqualTo(0L);
        assertThat(coupon.getMaxDiscountPrice()).isNull();
        assertThat(coupon.getTotalQuantity()).isEqualTo(100);
        assertThat(coupon.getIssuedQuantity()).isEqualTo(0);
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("Coupon 생성 성공 - 정률 쿠폰은 최대 할인 금액이 있으면 생성된다")
    void createCoupon_success_rateCoupon() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when
        Coupon coupon = new Coupon(
            "테스트 10퍼센트 할인 쿠폰",
            CouponDiscountType.RATE,
            10,
            10000L,
            5000L,
            100,
            now.minusDays(1),
            now.plusDays(30)
        );

        // then
        assertThat(coupon.getDiscountType()).isEqualTo(CouponDiscountType.RATE);
        assertThat(coupon.getDiscountValue()).isEqualTo(10);
        assertThat(coupon.getMinOrderPrice()).isEqualTo(10000L);
        assertThat(coupon.getMaxDiscountPrice()).isEqualTo(5000L);
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    @DisplayName("Coupon 생성 실패 - 쿠폰명이 없으면 예외가 발생한다")
    void createCoupon_fail_whenNameIsBlank() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
            " ",
            CouponDiscountType.FIXED,
            1000,
            0L,
            null,
            100,
            now.minusDays(1),
            now.plusDays(30)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Coupon 생성 실패 - 할인 타입이 없으면 예외가 발생한다")
    void createCoupon_fail_whenDiscountTypeIsNull() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
            "테스트 쿠폰",
            null,
            1000,
            0L,
            null,
            100,
            now.minusDays(1),
            now.plusDays(30)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Coupon 생성 실패 - 할인 값이 0 이하이면 예외가 발생한다")
    void createCoupon_fail_whenDiscountValueIsZeroOrNegative() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
            "테스트 쿠폰",
            CouponDiscountType.FIXED,
            0,
            0L,
            null,
            100,
            now.minusDays(1),
            now.plusDays(30)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Coupon 생성 실패 - 최소 주문 금액이 음수이면 예외가 발생한다")
    void createCoupon_fail_whenMinOrderPriceIsNegative() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
            "테스트 쿠폰",
            CouponDiscountType.FIXED,
            1000,
            -1L,
            null,
            100,
            now.minusDays(1),
            now.plusDays(30)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Coupon 생성 실패 - 총 발급 수량이 0 이하이면 예외가 발생한다")
    void createCoupon_fail_whenTotalQuantityIsZeroOrNegative() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
            "테스트 쿠폰",
            CouponDiscountType.FIXED,
            1000,
            0L,
            null,
            0,
            now.minusDays(1),
            now.plusDays(30)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Coupon 생성 실패 - 만료일이 시작일보다 빠르거나 같으면 예외가 발생한다")
    void createCoupon_fail_whenExpiredAtIsNotAfterStartAt() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
            "테스트 쿠폰",
            CouponDiscountType.FIXED,
            1000,
            0L,
            null,
            100,
            now,
            now
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Coupon 생성 실패 - 정률 쿠폰 할인율이 100을 초과하면 예외가 발생한다")
    void createCoupon_fail_whenRateDiscountValueIsOver100() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
            "테스트 정률 쿠폰",
            CouponDiscountType.RATE,
            101,
            0L,
            5000L,
            100,
            now.minusDays(1),
            now.plusDays(30)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Coupon 생성 실패 - 정률 쿠폰의 최대 할인 금액이 없으면 예외가 발생한다")
    void createCoupon_fail_whenRateCouponHasNoMaxDiscountPrice() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
            "테스트 정률 쿠폰",
            CouponDiscountType.RATE,
            10,
            0L,
            null,
            100,
            now.minusDays(1),
            now.plusDays(30)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Coupon 생성 실패 - 정액 쿠폰에 최대 할인 금액이 있으면 예외가 발생한다")
    void createCoupon_fail_whenFixedCouponHasMaxDiscountPrice() {
        // given
        LocalDateTime now = LocalDateTime.now();

        // when & then
        assertThatThrownBy(() -> new Coupon(
            "테스트 정액 쿠폰",
            CouponDiscountType.FIXED,
            1000,
            0L,
            5000L,
            100,
            now.minusDays(1),
            now.plusDays(30)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateIssuable 성공 - ACTIVE 상태이고 기간 내이며 수량이 남아 있으면 통과한다")
    void validateIssuable_success() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(30),
            100
        );

        // when & then
        assertThatNoException().isThrownBy(() -> coupon.validateIssuable(now));
    }

    @Test
    @DisplayName("validateIssuable 실패 - 비활성 쿠폰이면 예외가 발생한다")
    void validateIssuable_fail_whenInactive() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(30),
            100
        );

        coupon.deactivate();

        // when & then
        assertThatThrownBy(() -> coupon.validateIssuable(now))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("validateIssuable 실패 - 쿠폰 기간 전이면 예외가 발생한다")
    void validateIssuable_fail_whenBeforeStartAt() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.plusDays(1),
            now.plusDays(30),
            100
        );

        // when & then
        assertThatThrownBy(() -> coupon.validateIssuable(now))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("validateIssuable 실패 - 쿠폰이 만료되었으면 예외가 발생한다")
    void validateIssuable_fail_whenExpired() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(30),
            now.minusDays(1),
            100
        );

        // when & then
        assertThatThrownBy(() -> coupon.validateIssuable(now))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("validateIssuable 실패 - 발급 수량이 모두 소진되면 예외가 발생한다")
    void validateIssuable_fail_whenSoldOut() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(30),
            1
        );

        coupon.increaseIssuedQuantity();

        // when & then
        assertThatThrownBy(() -> coupon.validateIssuable(now))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("increaseIssuedQuantity 성공 - 발급 수량을 1 증가시킨다")
    void increaseIssuedQuantity_success() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(30),
            100
        );

        // when
        coupon.increaseIssuedQuantity();

        // then
        assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("increaseIssuedQuantity 실패 - 발급 수량이 모두 소진되면 예외가 발생한다")
    void increaseIssuedQuantity_fail_whenSoldOut() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(30),
            1
        );

        coupon.increaseIssuedQuantity();

        // when & then
        assertThatThrownBy(coupon::increaseIssuedQuantity)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("calculateDiscountPrice 성공 - 정액 쿠폰은 할인 금액만큼 할인한다")
    void calculateDiscountPrice_success_fixedCoupon() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(30),
            100
        );

        // when
        Long discountPrice = coupon.calculateDiscountPrice(10000L);

        // then
        assertThat(discountPrice).isEqualTo(1000L);
    }

    @Test
    @DisplayName("calculateDiscountPrice 성공 - 정액 쿠폰 할인 금액은 주문 금액을 초과할 수 없다")
    void calculateDiscountPrice_success_fixedCouponCannotExceedOrderPrice() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(30),
            100
        );

        // when
        Long discountPrice = coupon.calculateDiscountPrice(500L);

        // then
        assertThat(discountPrice).isEqualTo(500L);
    }

    @Test
    @DisplayName("calculateDiscountPrice 성공 - 정률 쿠폰은 주문 금액의 할인율만큼 할인한다")
    void calculateDiscountPrice_success_rateCoupon() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = rateCoupon(
            10,
            5000L,
            now.minusDays(1),
            now.plusDays(30)
        );

        // when
        Long discountPrice = coupon.calculateDiscountPrice(30000L);

        // then
        assertThat(discountPrice).isEqualTo(3000L);
    }

    @Test
    @DisplayName("calculateDiscountPrice 성공 - 정률 쿠폰은 최대 할인 금액을 초과할 수 없다")
    void calculateDiscountPrice_success_rateCouponCannotExceedMaxDiscountPrice() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = rateCoupon(
            50,
            5000L,
            now.minusDays(1),
            now.plusDays(30)
        );

        // when
        Long discountPrice = coupon.calculateDiscountPrice(20000L);

        // then
        assertThat(discountPrice).isEqualTo(5000L);
    }

    @Test
    @DisplayName("calculateDiscountPrice 성공 - 정률 쿠폰 할인 금액은 주문 금액을 초과할 수 없다")
    void calculateDiscountPrice_success_rateCouponCannotExceedOrderPrice() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = rateCoupon(
            100,
            10000L,
            now.minusDays(1),
            now.plusDays(30)
        );

        // when
        Long discountPrice = coupon.calculateDiscountPrice(5000L);

        // then
        assertThat(discountPrice).isEqualTo(5000L);
    }

    @Test
    @DisplayName("calculateDiscountPrice 실패 - 주문 금액이 null이면 예외가 발생한다")
    void calculateDiscountPrice_fail_whenOrderPriceIsNull() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(30),
            100
        );

        // when & then
        assertThatThrownBy(() -> coupon.calculateDiscountPrice(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("calculateDiscountPrice 실패 - 주문 금액이 음수이면 예외가 발생한다")
    void calculateDiscountPrice_fail_whenOrderPriceIsNegative() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(30),
            100
        );

        // when & then
        assertThatThrownBy(() -> coupon.calculateDiscountPrice(-1L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("calculateDiscountPrice 실패 - 최소 주문 금액을 충족하지 못하면 예외가 발생한다")
    void calculateDiscountPrice_fail_whenMinOrderPriceNotSatisfied() {
        // given
        LocalDateTime now = LocalDateTime.now();

        Coupon coupon = new Coupon(
            "최소 주문 금액 쿠폰",
            CouponDiscountType.FIXED,
            1000,
            10000L,
            null,
            100,
            now.minusDays(1),
            now.plusDays(30)
        );

        // when & then
        assertThatThrownBy(() -> coupon.calculateDiscountPrice(5000L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("deactivate 성공 - ACTIVE 쿠폰을 INACTIVE로 변경한다")
    void deactivate_success() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(30),
            100
        );

        // when
        coupon.deactivate();

        // then
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.INACTIVE);
    }

    @Test
    @DisplayName("deactivate 실패 - 이미 비활성화된 쿠폰이면 예외가 발생한다")
    void deactivate_fail_whenAlreadyInactive() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(30),
            100
        );

        coupon.deactivate();

        // when & then
        assertThatThrownBy(coupon::deactivate)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("isInPeriod 성공 - 현재 시각이 시작일과 만료일 사이이면 true를 반환한다")
    void isInPeriod_success_whenNowIsBetweenStartAtAndExpiredAt() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(1),
            100
        );

        // when
        boolean result = coupon.isInPeriod(now);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isInPeriod 성공 - 현재 시각이 시작일 전이면 false를 반환한다")
    void isInPeriod_success_whenNowIsBeforeStartAt() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.plusDays(1),
            now.plusDays(2),
            100
        );

        // when
        boolean result = coupon.isInPeriod(now);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isInPeriod 성공 - 현재 시각이 시작일과 같으면 true를 반환한다")
    void isInPeriod_success_whenNowEqualsStartAt() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now,
            now.plusDays(1),
            100
        );

        // when
        boolean result = coupon.isInPeriod(now);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isInPeriod 성공 - 현재 시각이 만료일과 같으면 true를 반환한다")
    void isInPeriod_success_whenNowEqualsExpiredAt() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now,
            100
        );

        // when
        boolean result = coupon.isInPeriod(now);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isExpired 성공 - 현재 시각이 만료일 이후이면 true를 반환한다")
    void isExpired_success_whenNowIsAfterExpiredAt() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(2),
            now.minusDays(1),
            100
        );

        // when
        boolean result = coupon.isExpired(now);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isExpired 성공 - 현재 시각이 만료일 이전이면 false를 반환한다")
    void isExpired_success_whenNowIsBeforeExpiredAt() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(
            now.minusDays(1),
            now.plusDays(1),
            100
        );

        // when
        boolean result = coupon.isExpired(now);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isExpired 성공 - 현재 시각이 만료일과 같으면 false를 반환한다")
    void isExpired_success_whenNowEqualsExpiredAt() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = fixedCoupon(now.minusDays(30), now, 100);

        // when
        boolean result = coupon.isExpired(now);

        // then
        assertThat(result).isFalse();
    }

    private Coupon fixedCoupon(
        LocalDateTime startAt,
        LocalDateTime expiredAt,
        Integer totalQuantity
    ) {
        return new Coupon(
            "테스트 정액 쿠폰",
            CouponDiscountType.FIXED,
            1000,
            0L,
            null,
            totalQuantity,
            startAt,
            expiredAt
        );
    }

    private Coupon rateCoupon(
        Integer discountValue,
        Long maxDiscountPrice,
        LocalDateTime startAt,
        LocalDateTime expiredAt
    ) {
        return new Coupon(
            "테스트 정률 쿠폰",
            CouponDiscountType.RATE,
            discountValue,
            0L,
            maxDiscountPrice,
            100,
            startAt,
            expiredAt
        );
    }

}
