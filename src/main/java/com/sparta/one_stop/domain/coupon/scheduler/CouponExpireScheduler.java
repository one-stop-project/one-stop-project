package com.sparta.one_stop.domain.coupon.scheduler;

import com.sparta.one_stop.domain.coupon.service.CouponExpireService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class CouponExpireScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CouponExpireService couponExpireService;

    /**
     * 매일 새벽 1시 (Asia/Seoul) — 만료 기간이 지난 쿠폰 일괄 처리
     * - AVAILABLE UserCoupon → EXPIRED
     * - ACTIVE Coupon → INACTIVE
     */
    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void runDailyExpiration() {
        LocalDateTime now = LocalDateTime.now(KST);
        log.info("[COUPON_EXPIRE] 시작 — now={}", now);
        try {
            couponExpireService.expireAll(now);
            log.info("[COUPON_EXPIRE] 종료");
        } catch (Exception e) {
            log.error("[COUPON_EXPIRE] 실행 실패", e);
        }
    }
}
