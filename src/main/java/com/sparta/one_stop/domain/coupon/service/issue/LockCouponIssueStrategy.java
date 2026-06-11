package com.sparta.one_stop.domain.coupon.service.issue;

import com.sparta.one_stop.domain.coupon.dto.response.IssueCouponResponse;
import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.domain.coupon.repository.UserCouponRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component("lockCouponIssueStrategy")
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
     * - 발급 수량 증가 시점에 쿠폰 ACTIVE 상태를 다시 검증하여 비활성 쿠폰 발급을 방지
     * - Redis Lock은 DB 트랜잭션 commit/rollback 완료 후 afterCompletion 콜백에서 해제
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

        try {
            boolean locked = lock.tryLock(
                LOCK_WAIT_TIME,
                LOCK_LEASE_TIME,
                TimeUnit.SECONDS
            );

            if (!locked) {
                throw new CustomException(ErrorCode.COMMON_008);
            }

            registerUnlockAfterTransactionCompletion(lock);

            return issueWithLock(
                userId,
                couponId
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.COMMON_008);
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

            int updatedCount = couponRepository.increaseIssuedQuantity(
                couponId,
                CouponStatus.ACTIVE
            );

            if (updatedCount == 0) {
                handleIncreaseIssuedQuantityFailure(couponId);
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

    /**
     * 발급 수량 증가 실패 원인 분기
     * - UPDATE 조건에 ACTIVE 상태와 잔여 수량 조건이 포함되어 있으므로 updatedCount가 0이면
     *   쿠폰 비활성화 또는 수량 소진 가능성이 있다.
     * - 쿠폰을 재조회하여 비활성 상태와 수량 소진을 구분한다.
     */
    private void handleIncreaseIssuedQuantityFailure(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CustomException(ErrorCode.COUPON_004));

        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            throw new CustomException(ErrorCode.COUPON_010);
        }

        throw new CustomException(ErrorCode.COUPON_001);
    }

    /**
     * Redis Lock 해제를 트랜잭션 종료 이후로 지연한다.
     * - @Transactional 메서드 내부 finally에서 unlock하면 DB commit보다 먼저 Lock이 해제될 수 있다.
     * - afterCompletion은 commit 또는 rollback 완료 후 실행되므로 직접 unlock으로 인한 pre-commit 상태 조회 윈도우를 방지한다.
     * - 트랜잭션 동기화 콜백 등록에 실패하면 Lock 누수를 막기 위해 즉시 unlock 후 예외를 발생시킨다.
     */
    private void registerUnlockAfterTransactionCompletion(RLock lock) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.error("트랜잭션 동기화가 활성화되어 있지 않아 Redis Lock 해제 콜백을 등록할 수 없습니다.");

            unlockQuietly(lock);

            throw new CustomException(ErrorCode.COUPON_012);
        }

        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    unlockQuietly(lock);
                }
            }
        );
    }

    /**
     * Redis Lock 해제 실패가 원래 비즈니스 예외를 덮지 않도록 방어적으로 처리한다.
     * - 현재 스레드가 보유한 Lock인 경우에만 unlock을 시도한다.
     * - unlock 실패는 예외를 전파하지 않고 로그로만 기록한다.
     */
    private void unlockQuietly(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Throwable e) {
            log.error("Redis Lock 해제 실패", e);
        }
    }

}
