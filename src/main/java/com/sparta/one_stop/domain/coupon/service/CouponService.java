package com.sparta.one_stop.domain.coupon.service;

import com.sparta.one_stop.domain.coupon.dto.response.AvailableCouponResponse;
import com.sparta.one_stop.domain.coupon.dto.response.IssueCouponResponse;
import com.sparta.one_stop.domain.coupon.dto.response.MyCouponPageResponse;
import com.sparta.one_stop.domain.coupon.dto.response.MyCouponResponse;
import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.domain.coupon.repository.UserCouponRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private static final String COUPON_STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String COUPON_ISSUED_USERS_KEY_PREFIX = "coupon:issued-users:";

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    // 발급 가능 쿠폰 목록 조회
    @Transactional(readOnly = true)
    public List<AvailableCouponResponse> getAvailableCoupons() {
        LocalDateTime now = LocalDateTime.now();

        List<Coupon> coupons = couponRepository.findAvailableCoupons(
            CouponStatus.ACTIVE,
            now
        );

        return coupons.stream()
            .map(this::toAvailableCouponResponse)
            .toList();
    }

    // 선착순 쿠폰 발급
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

        validateNotIssuedInRedis(
            issuedUsersKey,
            userIdValue
        );

        initializeCouponStockIfAbsent(
            coupon,
            stockKey,
            now
        );

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

            UserCoupon userCoupon = new UserCoupon(
                user,
                coupon
            );

            UserCoupon savedUserCoupon = userCouponRepository.saveAndFlush(userCoupon);

            int updatedCount = couponRepository.increaseIssuedQuantity(couponId);

            if (updatedCount == 0) {
                increaseRedisStock(stockKey);
                throw new CustomException(ErrorCode.COUPON_001);
            }

            redisTemplate.opsForSet()
                .add(
                    issuedUsersKey,
                    userIdValue
                );

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

    // 내 쿠폰 목록 조회
    @Transactional(readOnly = true)
    public MyCouponPageResponse getMyCoupons(
        Long userId,
        UserCouponStatus status,
        Pageable pageable
    ) {
        if (userId == null) {
            throw new CustomException(ErrorCode.AUTH_007);
        }

        Page<UserCoupon> userCouponPage;

        if (status == null) {
            userCouponPage = userCouponRepository.findAllByUserId(
                userId,
                pageable
            );
        } else {
            userCouponPage = userCouponRepository.findAllByUserIdAndStatus(
                userId,
                status,
                pageable
            );
        }

        List<MyCouponResponse> content = userCouponPage.getContent()
            .stream()
            .map(this::toMyCouponResponse)
            .toList();

        return new MyCouponPageResponse(
            content,
            userCouponPage.getNumber(),
            userCouponPage.getSize(),
            userCouponPage.getTotalElements(),
            userCouponPage.getTotalPages()
        );
    }

    // == Redis 처리 메서드 ==

    // Redis에 쿠폰 잔여 수량이 없으면 DB 기준 잔여 수량으로 초기화
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

    // Redis Set 기준 중복 발급 여부 확인
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

    // Redis 잔여 수량 차감
    private Long decreaseRedisStock(String stockKey) {
        return redisTemplate.opsForValue()
            .decrement(stockKey);
    }

    // Redis 잔여 수량 보상 증가
    private void increaseRedisStock(String stockKey) {
        redisTemplate.opsForValue()
            .increment(stockKey);
    }

    // Redis Set 만료 시간 설정
    private void applyExpireAtToRedisKey(
        String key,
        LocalDateTime expiredAt,
        LocalDateTime now
    ) {
        Duration ttl = Duration.between(
            now,
            expiredAt
        );

        if (!ttl.isNegative() && !ttl.isZero()) {
            redisTemplate.expire(
                key,
                ttl
            );
        }
    }

    private String getCouponStockKey(Long couponId) {
        return COUPON_STOCK_KEY_PREFIX + couponId;
    }

    private String getCouponIssuedUsersKey(Long couponId) {
        return COUPON_ISSUED_USERS_KEY_PREFIX + couponId;
    }

    // == 검증 메서드 ==

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

    private AvailableCouponResponse toAvailableCouponResponse(Coupon coupon) {
        return new AvailableCouponResponse(
            coupon.getId(),
            coupon.getName(),
            coupon.getDiscountType(),
            coupon.getDiscountValue(),
            coupon.getMinOrderPrice(),
            coupon.getMaxDiscountPrice(),
            coupon.getTotalQuantity() - coupon.getIssuedQuantity(),
            coupon.getStartAt(),
            coupon.getExpiredAt()
        );
    }

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

    private MyCouponResponse toMyCouponResponse(UserCoupon userCoupon) {
        Coupon coupon = userCoupon.getCoupon();

        return new MyCouponResponse(
            userCoupon.getId(),
            coupon.getId(),
            coupon.getName(),
            coupon.getDiscountType(),
            coupon.getDiscountValue(),
            coupon.getMinOrderPrice(),
            coupon.getMaxDiscountPrice(),
            userCoupon.getStatus(),
            userCoupon.getCreatedAt(),
            userCoupon.getUsedAt(),
            coupon.getExpiredAt()
        );
    }

}
