package com.sparta.one_stop.domain.coupon.service;

import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.domain.coupon.repository.UserCouponRepository;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponExpireService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 만료 기간이 지난 쿠폰 일괄 처리
     * 1. AVAILABLE 상태의 UserCoupon 중 쿠폰 expiredAt이 지난 것 → EXPIRED
     * 2. ACTIVE 상태의 Coupon 중 expiredAt이 지난 것 → INACTIVE
     * UserCoupon을 먼저 처리해야 Coupon.status 변경에 영향받지 않는다
     */
    @Transactional
    public void expireAll(LocalDateTime now) {
        int expiredUserCoupons = userCouponRepository.bulkExpireAvailableByExpiredCoupons(
            UserCouponStatus.AVAILABLE,
            UserCouponStatus.EXPIRED,
            now
        );
        log.info("[COUPON_EXPIRE] UserCoupon 만료 처리 count={}", expiredUserCoupons);

        int deactivatedCoupons = couponRepository.bulkDeactivateExpired(
            CouponStatus.ACTIVE,
            CouponStatus.INACTIVE,
            now
        );
        log.info("[COUPON_EXPIRE] Coupon 비활성화 처리 count={}", deactivatedCoupons);
    }
}
