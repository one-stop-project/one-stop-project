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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component("decrCouponIssueStrategy")
@ConditionalOnProperty(
    name = "coupon.issue.strategy",
    havingValue = "decr",
    matchIfMissing = true
)
@RequiredArgsConstructor
public class DecrCouponIssueStrategy implements CouponIssueStrategy {

    private static final String COUPON_STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String COUPON_ISSUED_USERS_KEY_PREFIX = "coupon:issued-users:";

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean supports(String strategyType) {
        return "decr".equalsIgnoreCase(strategyType);
    }

    /**
     * Redis DECR 기반 선착순 쿠폰 발급
     * - Redis Set 기준 중복 발급 1차 검증
     * - Redis DECR 명령으로 쿠폰 잔여 수량 원자적 차감
     * - DB Unique 제약 기반 중복 발급 2차 검증
     * - DB 처리 중 예외 발생 시 Redis 수량 및 발급 사용자 Set 보상
     * - 트랜잭션 커밋 실패로 롤백되는 경우 afterCompletion 콜백에서 Redis 보상
     * - AtomicBoolean으로 즉시 보상과 롤백 보상이 중복 실행되지 않도록 방지
     */
    @Override
    @Transactional
    public IssueCouponResponse issue(
        Long userId,
        Long couponId
    ) {
        validateRequiredId(userId, couponId);

        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));

        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CustomException(ErrorCode.COUPON_004));

        coupon.validateIssuable(now);

        String stockKey = getCouponStockKey(couponId);
        String issuedUsersKey = getCouponIssuedUsersKey(couponId);
        String userIdValue = String.valueOf(userId);

        validateNotIssuedInRedis(issuedUsersKey, userIdValue);
        initializeCouponStockIfAbsent(coupon, stockKey, now);

        AtomicBoolean stockCompensationRequired = new AtomicBoolean(false);
        AtomicBoolean issuedUserCompensationRequired = new AtomicBoolean(false);

        registerRollbackCompensation(() -> compensateRedisIssue(
            stockKey,
            issuedUsersKey,
            userIdValue,
            stockCompensationRequired,
            issuedUserCompensationRequired
        ));

        try {
            Long remainingStock = decreaseRedisStock(stockKey);

            if (remainingStock == null) {
                throw new CustomException(ErrorCode.COUPON_012);
            }

            if (remainingStock < 0) {
                increaseRedisStock(stockKey);
                throw new CustomException(ErrorCode.COUPON_001);
            }

            // Redis DECR 성공 이후부터는 DB 처리 실패 또는 트랜잭션 롤백 시 재고 보상이 필요하다
            stockCompensationRequired.set(true);

            if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
                throw new CustomException(ErrorCode.COUPON_002);
            }

            UserCoupon userCoupon = new UserCoupon(user, coupon);
            UserCoupon savedUserCoupon = userCouponRepository.saveAndFlush(userCoupon);

            int updatedCount = couponRepository.increaseIssuedQuantity(
                couponId,
                CouponStatus.ACTIVE
            );

            if (updatedCount == 0) {
                handleIncreaseIssuedQuantityFailure(couponId);
            }

            Long addedCount = redisTemplate.opsForSet()
                .add(issuedUsersKey, userIdValue);

            // 이번 요청에서 issued-users Set에 실제로 추가된 경우에만 롤백 시 제거한다
            if (addedCount != null && addedCount > 0) {
                issuedUserCompensationRequired.set(true);
            }

            applyExpireAtToRedisKey(
                issuedUsersKey,
                coupon.getExpiredAt(),
                now
            );

            return IssueCouponResponse.of(savedUserCoupon);
        } catch (DataIntegrityViolationException e) {
            compensateRedisIssue(
                stockKey,
                issuedUsersKey,
                userIdValue,
                stockCompensationRequired,
                issuedUserCompensationRequired
            );

            throw new CustomException(ErrorCode.COUPON_002);
        } catch (CustomException e) {
            compensateRedisIssue(
                stockKey,
                issuedUsersKey,
                userIdValue,
                stockCompensationRequired,
                issuedUserCompensationRequired
            );

            throw e;
        } catch (RuntimeException e) {
            compensateRedisIssue(
                stockKey,
                issuedUsersKey,
                userIdValue,
                stockCompensationRequired,
                issuedUserCompensationRequired
            );

            throw e;
        }
    }

    private void initializeCouponStockIfAbsent(
        Coupon coupon,
        String stockKey,
        LocalDateTime now
    ) {
        Integer remainingQuantity = coupon.getTotalQuantity() - coupon.getIssuedQuantity();

        if (remainingQuantity <= 0) {
            throw new CustomException(ErrorCode.COUPON_001);
        }

        Duration ttl = Duration.between(now, coupon.getExpiredAt());

        if (ttl.isNegative() || ttl.isZero()) {
            throw new CustomException(ErrorCode.COUPON_007);
        }

        redisTemplate.opsForValue()
            .setIfAbsent(
                stockKey,
                String.valueOf(remainingQuantity),
                ttl
            );
    }

    private void validateNotIssuedInRedis(
        String issuedUsersKey,
        String userIdValue
    ) {
        Boolean alreadyIssued = redisTemplate.opsForSet()
            .isMember(
                issuedUsersKey,
                userIdValue
            );

        if (Boolean.TRUE.equals(alreadyIssued)) {
            throw new CustomException(ErrorCode.COUPON_002);
        }
    }

    private Long decreaseRedisStock(String stockKey) {
        return redisTemplate.opsForValue()
            .decrement(stockKey);
    }

    private void increaseRedisStock(String stockKey) {
        redisTemplate.opsForValue()
            .increment(stockKey);
    }

    private void applyExpireAtToRedisKey(
        String key,
        LocalDateTime expiredAt,
        LocalDateTime now
    ) {
        long ttlSeconds = Duration.between(now, expiredAt).getSeconds();

        if (ttlSeconds <= 0) {
            return;
        }

        try {
            DefaultRedisScript<Long> expireScript = new DefaultRedisScript<>(
                "return redis.call('expire', KEYS[1], ARGV[1])",
                Long.class
            );

            Long result = redisTemplate.execute(
                expireScript,
                List.of(key),
                String.valueOf(ttlSeconds)
            );

            log.info(
                "Redis key TTL 설정 결과 - key: {}, ttlSeconds: {}, result: {}",
                key,
                ttlSeconds,
                result
            );
        } catch (Throwable e) {
            log.warn(
                "Redis key TTL 설정 실패 - key: {}, ttlSeconds: {}",
                key,
                ttlSeconds,
                e
            );
        }
    }

    private String getCouponStockKey(Long couponId) {
        return COUPON_STOCK_KEY_PREFIX + couponId;
    }

    private String getCouponIssuedUsersKey(Long couponId) {
        return COUPON_ISSUED_USERS_KEY_PREFIX + couponId;
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

    private void registerRollbackCompensation(Runnable compensation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.error("트랜잭션 동기화가 활성화되어 있지 않아 Redis 롤백 보상 콜백을 등록할 수 없습니다.");
            throw new CustomException(ErrorCode.COUPON_012);
        }

        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        compensation.run();
                    }
                }
            }
        );
    }

    private void compensateRedisIssue(
        String stockKey,
        String issuedUsersKey,
        String userIdValue,
        AtomicBoolean stockCompensationRequired,
        AtomicBoolean issuedUserCompensationRequired
    ) {
        if (stockCompensationRequired.compareAndSet(true, false)) {
            try {
                increaseRedisStock(stockKey);

                log.info(
                    "Redis 쿠폰 재고 보상 완료 - stockKey: {}",
                    stockKey
                );
            } catch (Throwable e) {
                log.error(
                    "Redis 쿠폰 재고 보상 실패 - stockKey: {}",
                    stockKey,
                    e
                );
            }
        }

        if (issuedUserCompensationRequired.compareAndSet(true, false)) {
            try {
                redisTemplate.opsForSet()
                    .remove(
                        issuedUsersKey,
                        userIdValue
                    );

                log.info(
                    "Redis 쿠폰 발급 사용자 보상 완료 - issuedUsersKey: {}, userId: {}",
                    issuedUsersKey,
                    userIdValue
                );
            } catch (Throwable e) {
                log.error(
                    "Redis 쿠폰 발급 사용자 보상 실패 - issuedUsersKey: {}, userId: {}",
                    issuedUsersKey,
                    userIdValue,
                    e
                );
            }
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

}
