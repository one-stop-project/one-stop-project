package com.sparta.one_stop.domain.coupon.service;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

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

            applyExpireAtToRedisKey(
                issuedUsersKey,
                coupon.getExpiredAt(),
                now
            );

            return toIssueCouponResponse(savedUserCoupon);
        } catch (DataIntegrityViolationException e) {
            increaseRedisStock(stockKey);
            throw new CustomException(ErrorCode.COUPON_002);
        } catch (CustomException e) {
            throw e;
        } catch (RuntimeException e) {
            increaseRedisStock(stockKey);
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
     * - 이미 만료되었거나 TTL이 0 이하이면 만료 시간을 설정하지 않음
     */
    private void applyExpireAtToRedisKey(
        String key,
        LocalDateTime expiredAt,
        LocalDateTime now
    ) {
        Duration ttl = Duration.between(now, expiredAt);

        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.expire(key, ttl);
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

    // == 응답 변환 메서드 ==

    /**
     * 쿠폰 발급 응답 DTO 변환
     * - 발급된 UserCoupon 기준으로 응답 생성
     * - createdAt은 쿠폰 발급일로 사용
     * - expiredAt은 쿠폰 마스터의 발급/사용 만료일을 사용
     */
    private IssueCouponResponse toIssueCouponResponse(UserCoupon userCoupon) {
        Coupon coupon = userCoupon.getCoupon();

        return new IssueCouponResponse(
            userCoupon.getId(),
            coupon.getId(),
            coupon.getName(),
            userCoupon.getStatus(),
            userCoupon.getCreatedAt(),
            coupon.getExpiredAt()
        );
    }

}
