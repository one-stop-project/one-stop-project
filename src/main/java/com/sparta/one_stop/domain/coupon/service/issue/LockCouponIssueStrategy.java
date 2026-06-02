package com.sparta.one_stop.domain.coupon.service.issue;

import com.sparta.one_stop.domain.coupon.dto.response.IssueCouponResponse;
import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.domain.coupon.repository.UserCouponRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component("lockCouponIssueStrategy")
@ConditionalOnProperty(
    name = "coupon.issue.strategy",
    havingValue = "lock"
)
@RequiredArgsConstructor
public class LockCouponIssueStrategy implements CouponIssueStrategy {

    private static final String COUPON_ISSUE_LOCK_KEY_PREFIX = "lock:coupon:issue:";

    private static final long LOCK_WAIT_TIME = 3L;
    private static final long LOCK_LEASE_TIME = 5L;

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final RedissonClient redissonClient;

    @Override
    public boolean supports(String strategyType) {
        return "lock".equalsIgnoreCase(strategyType);
    }

    /**
     * Redis Lock 기반 선착순 쿠폰 발급
     * - 쿠폰 단위 Lock을 획득하여 동일 쿠폰 발급 요청을 순차 처리
     * - Lock 내부에서 쿠폰 발급 가능 여부, 중복 발급 여부, 수량 증가를 처리
     * - DB Unique 제약으로 중복 발급을 최종 방어
     */
    @Override
    @Transactional
    public IssueCouponResponse issue(
        Long userId,
        Long couponId
    ) {
        validateRequiredId(
            userId,
            couponId
        );

        String lockKey = getCouponIssueLockKey(couponId);
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;

        try {
            locked = lock.tryLock(
                LOCK_WAIT_TIME,
                LOCK_LEASE_TIME,
                TimeUnit.SECONDS
            );

            if (!locked) {
                throw new CustomException(ErrorCode.COMMON_008);
            }

            return issueWithLock(
                userId,
                couponId
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.COMMON_008);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private IssueCouponResponse issueWithLock(
        Long userId,
        Long couponId
    ) {
        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));

        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CustomException(ErrorCode.COUPON_004));

        coupon.validateIssuable(now);

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new CustomException(ErrorCode.COUPON_002);
        }

        try {
            UserCoupon userCoupon = new UserCoupon(
                user,
                coupon
            );

            UserCoupon savedUserCoupon = userCouponRepository.saveAndFlush(userCoupon);

            int updatedCount = couponRepository.increaseIssuedQuantity(couponId);

            if (updatedCount == 0) {
                throw new CustomException(ErrorCode.COUPON_001);
            }

            return IssueCouponResponse.of(savedUserCoupon);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.COUPON_002);
        }
    }

    private String getCouponIssueLockKey(Long couponId) {
        return COUPON_ISSUE_LOCK_KEY_PREFIX + couponId;
    }

    private void validateRequiredId(
        Long userId,
        Long couponId
    ) {
        if (userId == null) {
            throw new CustomException(ErrorCode.AUTH_007);
        }

        if (couponId == null) {
            throw new CustomException(ErrorCode.COUPON_004);
        }
    }

}
