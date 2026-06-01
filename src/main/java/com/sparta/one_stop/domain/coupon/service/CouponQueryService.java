package com.sparta.one_stop.domain.coupon.service;

import com.sparta.one_stop.domain.coupon.dto.response.AvailableCouponResponse;
import com.sparta.one_stop.domain.coupon.dto.response.MyCouponPageResponse;
import com.sparta.one_stop.domain.coupon.dto.response.MyCouponResponse;
import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.domain.coupon.repository.UserCouponRepository;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponQueryService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    /**
     * 발급 가능 쿠폰 목록 조회
     * - ACTIVE 상태 쿠폰만 조회
     * - 현재 시각 기준 발급/사용 기간 내 쿠폰만 조회
     * - 발급 가능 수량이 남아 있는 쿠폰만 조회
     * - 쿠폰 마스터 정보를 발급 가능 쿠폰 응답 DTO로 변환
     */
    public List<AvailableCouponResponse> getAvailableCoupons() {
        LocalDateTime now = LocalDateTime.now();

        return couponRepository.findAvailableCoupons(
                CouponStatus.ACTIVE,
                now
            )
            .stream()
            .map(this::toAvailableCouponResponse)
            .toList();
    }

    /**
     * 내 쿠폰 목록 조회
     * - 로그인 사용자 기준으로 발급받은 쿠폰 목록 조회
     * - status 값이 없으면 전체 쿠폰 조회
     * - status 값이 있으면 AVAILABLE / USED / EXPIRED 상태별 필터 조회
     * - 페이징 정보를 포함하여 응답
     */
    public MyCouponPageResponse getMyCoupons(
        Long userId,
        UserCouponStatus status,
        Pageable pageable
    ) {
        if (userId == null) {
            throw new CustomException(ErrorCode.AUTH_007);
        }

        Page<UserCoupon> userCouponPage = status == null
            ? userCouponRepository.findAllByUserId(userId, pageable)
            : userCouponRepository.findAllByUserIdAndStatus(userId, status, pageable);

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

    /**
     * 발급 가능 쿠폰 응답 DTO 변환
     * - Coupon 마스터 정보를 기반으로 응답 생성
     * - remainingQuantity는 totalQuantity - issuedQuantity로 계산
     */
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

    /**
     * 내 쿠폰 응답 DTO 변환
     * - UserCoupon 기준으로 사용자 쿠폰 상태를 응답
     * - Coupon 마스터 정보를 함께 내려주어 할인 조건을 확인할 수 있도록 처리
     * - createdAt은 쿠폰 발급일로 사용
     */
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
