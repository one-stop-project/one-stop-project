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
    private static final Long REDIS_STOCK_KEY_NOT_FOUND = -3L;
    private static final Long REDIS_STOCK_KEY_NO_TTL = -4L;

    /**
     * Redis stock key 안전 차감 스크립트
     * 목적:
     * - stock key가 TTL 만료로 사라진 상태에서 DECR이 실행되면 Redis가 TTL 없는 -1 key를 새로 생성할 수 있다.
     * - 이 경우 이후 setIfAbsent가 실패하여 쿠폰이 영구 소진 상태처럼 남을 수 있다.
     * 반환값:
     * - -3: stock key가 존재하지 않음
     * - -4: stock key는 존재하지만 TTL이 없음
     * - 그 외: DECR 수행 결과
     */
    private static final DefaultRedisScript<Long> SAFE_DECR_SCRIPT =
        new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -3
            end

            local ttl = redis.call('PTTL', KEYS[1])

            if ttl == -1 then
                return -4
            end

            return redis.call('DECR', KEYS[1])
            """,
            Long.class
        );

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
     * - Lua Script로 Redis stock key 존재 여부와 TTL을 검증한 뒤 DECR 수행
     * - stock key가 없거나 TTL이 없는 비정상 key이면 DB 기준으로 재초기화 후 1회 재시도
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
            Long remainingStock = decreaseRedisStockSafely(
                couponId,
                stockKey,
                now
            );

            if (remainingStock == null) {
                throw new CustomException(ErrorCode.COUPON_012);
            }

            // SAFE_DECR_SCRIPT의 -3, -4 비정상 key 결과는 decreaseRedisStockSafely()에서 먼저 처리된다.
            // 이 분기는 정상 TTL key에서 DECR 결과가 음수가 된 경우, 즉 Redis 기준 수량 소진 상황이다.
            // DECR로 감소된 값을 다시 1 증가시켜 0으로 복구한 뒤 수량 소진 예외를 반환한다.
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
        return redisTemplate.execute(
            SAFE_DECR_SCRIPT,
            List.of(stockKey)
        );
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

    private Long decreaseRedisStockSafely(
        Long couponId,
        String stockKey,
        LocalDateTime now
    ) {
        Long remainingStock = decreaseRedisStock(stockKey);

        if (isInvalidStockKeyResult(remainingStock)) {
            reinitializeCouponStock(
                couponId,
                stockKey,
                now
            );

            remainingStock = decreaseRedisStock(stockKey);
        }

        if (isInvalidStockKeyResult(remainingStock)) {
            throw new CustomException(ErrorCode.COUPON_012);
        }

        return remainingStock;
    }

    private boolean isInvalidStockKeyResult(Long result) {
        return REDIS_STOCK_KEY_NOT_FOUND.equals(result)
            || REDIS_STOCK_KEY_NO_TTL.equals(result);
    }

    /**

     * Redis stock key 재초기화
     * 재초기화가 필요한 경우:
     * - stock key가 TTL 만료로 사라진 경우
     * - stock key가 존재하지만 TTL이 없는 비정상 key로 남은 경우
     * 처리 방식:
     * - DB에서 최신 Coupon을 다시 조회하여 잔여 수량을 계산한다.
     * - 기존 stock key를 삭제한 뒤 DB 기준 잔여 수량과 TTL로 다시 초기화한다.
     * 주의:
     * - TTL 없는 0 또는 음수 key가 남아 있을 수 있으므로 delete 후 setIfAbsent를 수행한다.
     * - 재초기화 후에도 안전 DECR이 실패하면 쿠폰 발급 처리 장애로 본다.
     */
    private void reinitializeCouponStock(
        Long couponId,
        String stockKey,
        LocalDateTime now
    ) {
        Coupon latestCoupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CustomException(ErrorCode.COUPON_004));

        latestCoupon.validateIssuable(now);

        Integer remainingQuantity =
            latestCoupon.getTotalQuantity() - latestCoupon.getIssuedQuantity();

        if (remainingQuantity <= 0) {
            throw new CustomException(ErrorCode.COUPON_001);
        }

        Duration ttl = Duration.between(now, latestCoupon.getExpiredAt());

        if (ttl.isNegative() || ttl.isZero()) {
            throw new CustomException(ErrorCode.COUPON_007);
        }

        redisTemplate.delete(stockKey);

        redisTemplate.opsForValue()
            .setIfAbsent(
                stockKey,
                String.valueOf(remainingQuantity),
                ttl
            );
    }

}
