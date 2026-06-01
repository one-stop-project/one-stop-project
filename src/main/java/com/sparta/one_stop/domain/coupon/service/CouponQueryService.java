package com.sparta.one_stop.domain.coupon.service;

import com.sparta.one_stop.domain.coupon.dto.response.AvailableCouponResponse;
import com.sparta.one_stop.domain.coupon.dto.response.MyCouponPageResponse;
import com.sparta.one_stop.domain.coupon.dto.response.MyCouponResponse;
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
            .map(AvailableCouponResponse::of)
            .toList();
    }

    /**
     * 내 쿠폰 목록 조회
     * - 로그인 사용자 기준으로 발급받은 쿠폰 목록 조회
     * - status 값이 없으면 전체 쿠폰 조회
     * - status 값이 있으면 AVAILABLE / USED / EXPIRED 상태별 필터 조회
     * - UserCoupon 엔티티를 MyCouponResponse DTO로 변환
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

        Page<MyCouponResponse> responsePage = userCouponPage.map(MyCouponResponse::of);

        return MyCouponPageResponse.of(responsePage);
    }

}
