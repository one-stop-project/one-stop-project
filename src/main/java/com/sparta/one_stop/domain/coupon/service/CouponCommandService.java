package com.sparta.one_stop.domain.coupon.service;

import com.sparta.one_stop.domain.coupon.dto.CouponRestoreResult;
import com.sparta.one_stop.domain.coupon.dto.response.IssueCouponResponse;
import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.domain.coupon.repository.UserCouponRepository;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponCommandService {

    private static final String COUPON_STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String COUPON_ISSUED_USERS_KEY_PREFIX = "coupon:issued-users:";

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    /**
     * 선착순 쿠폰 발급
     * - 사용자 / 쿠폰 ID 필수값 검증
     * - 쿠폰 존재 여부 및 발급 가능 상태 검증
     * - Redis Set 기준 중복 발급 1차 검증
     * - Redis DECR 기반 선착순 잔여 수량 차감
     * - DB Unique 제약 기반 중복 발급 2차 검증
     * - UserCoupon 저장
     * - Coupon.issuedQuantity 원자 증가
     * - DB 저장 성공 후 Redis Set에 발급 사용자 기록
     * - DB 처리 실패 시 Redis 수량 보상 증가
     */
    @Transactional
    public IssueCouponResponse issueCoupon(
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

            // Redis Set에도 쿠폰 만료 시각 기준 TTL 적용
            // redisTemplate.expire() 대신 Lua Script로 EXPIRE 명령을 실행하여 로컬 Redis Connection 이슈를 우회한다
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

    // == Redis 처리 메서드 ==

    /**
     * Redis 쿠폰 잔여 수량 초기화
     * - Redis stock key가 없을 때만 DB 기준 잔여 수량으로 초기화
     * - 잔여 수량 = totalQuantity - issuedQuantity
     * - 쿠폰 만료 시각까지 TTL 설정
     * - 이미 Redis key가 있으면 기존 값을 유지
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

    /**
     * Redis Set 기준 중복 발급 여부 검증
     * - coupon:issued-users:{couponId} Set에 userId가 있으면 이미 발급된 사용자로 판단
     * - DB Unique 제약 전 빠른 중복 발급 차단 용도
     */
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

    /**
     * Redis 쿠폰 잔여 수량 차감
     * - Redis DECR 명령으로 원자적 차감 처리
     * - 반환값이 0 이상이면 발급 가능
     * - 반환값이 음수이면 수량 소진으로 판단하고 보상 증가 필요
     */
    private Long decreaseRedisStock(String stockKey) {
        return redisTemplate.opsForValue()
            .decrement(stockKey);
    }

    /**
     * Redis 쿠폰 잔여 수량 보상 증가
     * - Redis DECR 이후 DB 저장 실패, 중복 발급, 수량 초과 상황에서 호출
     * - Redis 수량과 DB 발급 결과의 정합성을 맞추기 위한 보상 처리
     */
    private void increaseRedisStock(String stockKey) {
        redisTemplate.opsForValue()
            .increment(stockKey);
    }

    /**
     * Redis key 만료 시간 설정
     * - 쿠폰 만료 시각 기준으로 Redis Set TTL 설정
     * - RedissonConnection 환경에서 redisTemplate.expire(), keyCommands().expire() 호출 시
     *   StackOverflowError가 발생하여 Lua Script로 EXPIRE 명령을 실행한다
     * - TTL 설정 실패가 쿠폰 발급 실패로 이어지지 않도록 warn 로그만 남긴다
     */
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

    /**
     * 쿠폰 잔여 수량 Redis key 생성
     */
    private String getCouponStockKey(Long couponId) {
        return COUPON_STOCK_KEY_PREFIX + couponId;
    }

    /**
     * 쿠폰 발급 사용자 Redis Set key 생성
     */
    private String getCouponIssuedUsersKey(Long couponId) {
        return COUPON_ISSUED_USERS_KEY_PREFIX + couponId;
    }

    // == 검증 메서드 ==

    /**
     * 쿠폰 발급 요청 필수 ID 검증
     * - 인증 사용자 ID가 없으면 인증 예외 처리
     * - 쿠폰 ID가 없으면 쿠폰 조회 실패 예외 처리
     */
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
     * 결제 성공 시 쿠폰 사용 처리
     * - 주문에 적용된 쿠폰이 없으면 처리하지 않음
     * - 결제 승인 성공 시점에 최종 사용 가능 여부를 검증
     * - UserCoupon 상태를 AVAILABLE → USED로 변경
     * - usedAt, usedOrder 저장
     */
    @Transactional
    public void useCouponByOrder(Order order) {
        if (order == null) {
            throw new CustomException(ErrorCode.ORDER_006);
        }

        UserCoupon userCoupon = order.getUserCoupon();

        if (userCoupon == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        userCoupon.validateUsable(
            order.getUser().getId(),
            now
        );

        userCoupon.use(
            order,
            now
        );
    }

    /**
     * 주문 취소 시 쿠폰 복구
     * - 주문에 적용된 쿠폰이 없으면 null 반환
     * - USED 상태 쿠폰만 복구 대상
     * - 쿠폰 만료 전이면 AVAILABLE로 복구
     * - 쿠폰 만료 후이면 EXPIRED로 변경
     * - 주문 취소 응답 생성을 위해 쿠폰 복구 결과 반환
     */
    @Transactional
    public CouponRestoreResult restoreCouponByOrder(Order order) {
        if (order == null) {
            throw new CustomException(ErrorCode.ORDER_006);
        }

        UserCoupon userCoupon = order.getUserCoupon();

        if (userCoupon == null) {
            return null;
        }

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        userCoupon.restore(now);

        return new CouponRestoreResult(
            userCoupon.getId(),
            userCoupon.getCoupon().getName(),
            userCoupon.getStatus()
        );
    }
}
