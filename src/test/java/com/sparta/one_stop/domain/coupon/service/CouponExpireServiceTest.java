package com.sparta.one_stop.domain.coupon.service;

import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.domain.coupon.repository.UserCouponRepository;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponExpireServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private CouponExpireService couponExpireService;

    @Test
    @DisplayName("expireAll 성공 - AVAILABLE UserCoupon을 EXPIRED로, ACTIVE Coupon을 INACTIVE로 일괄 처리한다")
    void expireAll_success_bulkUpdatesApplied() {
        // given
        LocalDateTime now = LocalDateTime.now();

        when(userCouponRepository.bulkExpireAvailableByExpiredCoupons(
            eq(UserCouponStatus.AVAILABLE),
            eq(UserCouponStatus.EXPIRED),
            eq(now)
        )).thenReturn(5);

        when(couponRepository.bulkDeactivateExpired(
            eq(CouponStatus.ACTIVE),
            eq(CouponStatus.INACTIVE),
            eq(now)
        )).thenReturn(3);

        // when & then
        assertThatNoException().isThrownBy(() -> couponExpireService.expireAll(now));

        verify(userCouponRepository).bulkExpireAvailableByExpiredCoupons(
            UserCouponStatus.AVAILABLE,
            UserCouponStatus.EXPIRED,
            now
        );
        verify(couponRepository).bulkDeactivateExpired(
            CouponStatus.ACTIVE,
            CouponStatus.INACTIVE,
            now
        );
    }

    @Test
    @DisplayName("expireAll 성공 - UserCoupon 만료를 Coupon 비활성화보다 먼저 처리한다")
    void expireAll_success_userCouponExpiredBeforeCouponDeactivated() {
        // given
        LocalDateTime now = LocalDateTime.now();

        when(userCouponRepository.bulkExpireAvailableByExpiredCoupons(any(), any(), any())).thenReturn(2);
        when(couponRepository.bulkDeactivateExpired(any(), any(), any())).thenReturn(1);

        // when
        couponExpireService.expireAll(now);

        // then
        InOrder inOrder = inOrder(userCouponRepository, couponRepository);
        inOrder.verify(userCouponRepository).bulkExpireAvailableByExpiredCoupons(any(), any(), any());
        inOrder.verify(couponRepository).bulkDeactivateExpired(any(), any(), any());
    }

    @Test
    @DisplayName("expireAll 성공 - 만료 대상이 없어도(count=0) 예외가 발생하지 않는다")
    void expireAll_success_whenNoTargets() {
        // given
        LocalDateTime now = LocalDateTime.now();

        when(userCouponRepository.bulkExpireAvailableByExpiredCoupons(any(), any(), any())).thenReturn(0);
        when(couponRepository.bulkDeactivateExpired(any(), any(), any())).thenReturn(0);

        // when & then
        assertThatNoException().isThrownBy(() -> couponExpireService.expireAll(now));
    }

    @Test
    @DisplayName("expireAll 성공 - 조회 기준 시각(now)을 두 쿼리 모두에 동일하게 전달한다")
    void expireAll_success_sameNowPassedToBothQueries() {
        // given
        LocalDateTime fixedNow = LocalDateTime.of(2026, 6, 19, 1, 0, 0);

        when(userCouponRepository.bulkExpireAvailableByExpiredCoupons(any(), any(), any())).thenReturn(0);
        when(couponRepository.bulkDeactivateExpired(any(), any(), any())).thenReturn(0);

        // when
        couponExpireService.expireAll(fixedNow);

        // then
        verify(userCouponRepository).bulkExpireAvailableByExpiredCoupons(any(), any(), eq(fixedNow));
        verify(couponRepository).bulkDeactivateExpired(any(), any(), eq(fixedNow));
    }
}
