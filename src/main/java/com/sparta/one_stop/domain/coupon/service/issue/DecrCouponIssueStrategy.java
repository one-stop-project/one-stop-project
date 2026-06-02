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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component("decrCouponIssueStrategy")
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
     * - DB 처리 실패 시 Redis 수량 보상 증가
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

        Long remainingStock = decreaseRedisStock(stockKey);

        if (remainingStock == null) {
            throw new CustomException(ErrorCode.COMMON_008);
        }

        if (remainingStock < 0) {
            increaseRedisStock(stockKey);
            throw new CustomException(ErrorCode.COUPON_001);
        }

        try {
            if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
                increaseRedisStock(stockKey);
                throw new CustomException(ErrorCode.COUPON_002);
            }

            UserCoupon userCoupon = new UserCoupon(user, coupon);
            UserCoupon savedUserCoupon = userCouponRepository.saveAndFlush(userCoupon);

            int updatedCount = couponRepository.increaseIssuedQuantity(couponId);

            if (updatedCount == 0) {
                increaseRedisStock(stockKey);
                throw new CustomException(ErrorCode.COUPON_001);
            }

            redisTemplate.opsForSet()
                .add(issuedUsersKey, userIdValue);

            applyExpireAtToRedisKey(
                issuedUsersKey,
                coupon.getExpiredAt(),
                now
            );

            return IssueCouponResponse.of(savedUserCoupon);
        } catch (DataIntegrityViolationException e) {
            increaseRedisStock(stockKey);
            throw new CustomException(ErrorCode.COUPON_002);
        } catch (CustomException e) {
            throw e;
        } catch (RuntimeException e) {
            increaseRedisStock(stockKey);

            redisTemplate.opsForSet()
                .remove(
                    issuedUsersKey,
                    userIdValue
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

}
