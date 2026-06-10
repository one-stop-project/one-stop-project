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
@Component("luaCouponIssueStrategy")
@ConditionalOnProperty(
    name = "coupon.issue.strategy",
    havingValue = "lua"
)
@RequiredArgsConstructor
public class LuaCouponIssueStrategy implements CouponIssueStrategy {

    private static final String COUPON_STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String COUPON_ISSUED_USERS_KEY_PREFIX = "coupon:issued-users:";

    private static final Long LUA_RESULT_SOLD_OUT = -1L;
    private static final Long LUA_RESULT_ALREADY_ISSUED = -2L;
    private static final Long LUA_RESULT_STOCK_NOT_INITIALIZED = -3L;

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean supports(String strategyType) {
        return "lua".equalsIgnoreCase(strategyType);
    }

    /**
     * Lua Script 기반 선착순 쿠폰 발급
     * - Redis Script 내부에서 중복 발급 확인, 수량 차감, 발급 사용자 기록을 원자적으로 처리
     * - DB Unique 제약으로 중복 발급을 최종 방어
     * - DB 처리 중 예외 발생 시 Redis stock / issued-users Set 보상 처리
     * - 트랜잭션 커밋 실패로 롤백되는 경우 afterCompletion 콜백에서 Redis 보상
     * - AtomicBoolean으로 즉시 보상과 롤백 보상이 중복 실행되지 않도록 방지
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

        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));

        Coupon coupon = couponRepository.findById(couponId)
            .orElseThrow(() -> new CustomException(ErrorCode.COUPON_004));

        coupon.validateIssuable(now);

        String stockKey = getCouponStockKey(couponId);
        String issuedUsersKey = getCouponIssuedUsersKey(couponId);
        String userIdValue = String.valueOf(userId);

        initializeCouponStockIfAbsent(
            coupon,
            stockKey,
            now
        );

        AtomicBoolean redisIssueCompensationRequired = new AtomicBoolean(false);

        registerRollbackCompensation(() -> compensateRedisIssue(
            stockKey,
            issuedUsersKey,
            userIdValue,
            redisIssueCompensationRequired
        ));

        try {
            Long luaResult = issueCouponInRedis(
                stockKey,
                issuedUsersKey,
                userIdValue,
                coupon.getExpiredAt(),
                now
            );

            validateLuaResult(luaResult);

            // Lua Script 성공 이후부터는 DB 처리 실패 또는 트랜잭션 롤백 시 Redis 발급 보상이 필요하다
            redisIssueCompensationRequired.set(true);

            if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
                throw new CustomException(ErrorCode.COUPON_002);
            }

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
            compensateRedisIssue(
                stockKey,
                issuedUsersKey,
                userIdValue,
                redisIssueCompensationRequired
            );

            throw new CustomException(ErrorCode.COUPON_002);
        } catch (CustomException e) {
            compensateRedisIssue(
                stockKey,
                issuedUsersKey,
                userIdValue,
                redisIssueCompensationRequired
            );

            throw e;
        } catch (RuntimeException e) {
            compensateRedisIssue(
                stockKey,
                issuedUsersKey,
                userIdValue,
                redisIssueCompensationRequired
            );

            throw e;
        }
    }

    /**
     * Redis 쿠폰 잔여 수량 초기화
     * - stock key가 없을 때만 DB 기준 잔여 수량으로 초기화
     * - stock key TTL은 쿠폰 만료 시각 기준으로 설정
     */
    private void initializeCouponStockIfAbsent(
        Coupon coupon,
        String stockKey,
        LocalDateTime now
    ) {
        Integer remainingQuantity = coupon.getTotalQuantity() - coupon.getIssuedQuantity();

        if (remainingQuantity <= 0) {
            throw new CustomException(ErrorCode.COUPON_001);
        }

        Duration ttl = Duration.between(
            now,
            coupon.getExpiredAt()
        );

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

    /**
     * Redis Lua Script 기반 발급 처리
     * - KEYS[1] = coupon stock key
     * - KEYS[2] = coupon issued users set key
     * - ARGV[1] = userId
     * - ARGV[2] = issued users set ttl seconds
     *
     * 반환값
     * - 0 이상: 발급 후 남은 수량
     * - -1: 수량 소진
     * - -2: 이미 발급된 사용자
     * - -3: stock key 미초기화
     */
    private Long issueCouponInRedis(
        String stockKey,
        String issuedUsersKey,
        String userIdValue,
        LocalDateTime expiredAt,
        LocalDateTime now
    ) {
        long ttlSeconds = Duration.between(
            now,
            expiredAt
        ).getSeconds();

        if (ttlSeconds <= 0) {
            throw new CustomException(ErrorCode.COUPON_007);
        }

        DefaultRedisScript<Long> issueScript = new DefaultRedisScript<>(
            """
            if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
                return -2
            end

            local stock = redis.call('get', KEYS[1])

            if not stock then
                return -3
            end

            if tonumber(stock) <= 0 then
                return -1
            end

            local remainingStock = redis.call('decr', KEYS[1])

            if remainingStock < 0 then
                redis.call('incr', KEYS[1])
                return -1
            end

            redis.call('sadd', KEYS[2], ARGV[1])
            redis.call('expire', KEYS[2], ARGV[2])

            return remainingStock
            """,
            Long.class
        );

        return redisTemplate.execute(
            issueScript,
            List.of(
                stockKey,
                issuedUsersKey
            ),
            userIdValue,
            String.valueOf(ttlSeconds)
        );
    }

    /**
     * Lua Script 결과 검증
     */
    private void validateLuaResult(Long luaResult) {
        if (luaResult == null) {
            throw new CustomException(ErrorCode.COUPON_012);
        }

        if (LUA_RESULT_SOLD_OUT.equals(luaResult)) {
            throw new CustomException(ErrorCode.COUPON_001);
        }

        if (LUA_RESULT_ALREADY_ISSUED.equals(luaResult)) {
            throw new CustomException(ErrorCode.COUPON_002);
        }

        if (LUA_RESULT_STOCK_NOT_INITIALIZED.equals(luaResult)) {
            throw new CustomException(ErrorCode.COUPON_012);
        }
    }

    /**
     * Redis 발급 보상 처리
     * - Lua Script 성공 후 DB 처리 중 예외가 발생하거나 트랜잭션이 롤백되면 호출
     * - 차감된 stock을 복구하고 issued-users Set에서 사용자 ID를 제거한다
     * - AtomicBoolean으로 즉시 보상과 롤백 콜백 보상이 중복 실행되지 않도록 방지
     */
    private void compensateRedisIssue(
        String stockKey,
        String issuedUsersKey,
        String userIdValue,
        AtomicBoolean redisIssueCompensationRequired
    ) {
        if (!redisIssueCompensationRequired.compareAndSet(true, false)) {
            return;
        }

        DefaultRedisScript<Long> compensateScript = new DefaultRedisScript<>(
            """
            redis.call('incr', KEYS[1])
            redis.call('srem', KEYS[2], ARGV[1])
            return 1
            """,
            Long.class
        );

        try {
            redisTemplate.execute(
                compensateScript,
                List.of(
                    stockKey,
                    issuedUsersKey
                ),
                userIdValue
            );

            log.info(
                "Redis 쿠폰 발급 보상 처리 완료 - stockKey: {}, issuedUsersKey: {}, userId: {}",
                stockKey,
                issuedUsersKey,
                userIdValue
            );
        } catch (Throwable e) {
            log.error(
                "Redis 쿠폰 발급 보상 처리 실패 - stockKey: {}, issuedUsersKey: {}, userId: {}",
                stockKey,
                issuedUsersKey,
                userIdValue,
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

    /**
     * 트랜잭션 롤백 시 Redis 발급 보상을 수행할 콜백을 등록한다.
     */
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
