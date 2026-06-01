package com.sparta.one_stop.domain.coupon.repository;

import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    // 동일 사용자 + 동일 쿠폰 발급 여부 확인
    // 중복 발급 방지에 사용한다
    boolean existsByUserIdAndCouponId(
        Long userId,
        Long couponId
    );

    // 사용자 쿠폰 단건 조회
    // 주문 생성 시 본인 쿠폰 여부, 상태, 기간, 최소 주문금액 검증에 사용한다
    Optional<UserCoupon> findByIdAndUserId(
        Long userCouponId,
        Long userId
    );

    // 사용자 쿠폰 전체 목록 조회
    // 마이페이지 내 쿠폰 목록 조회 시 사용한다
    Page<UserCoupon> findAllByUserId(
        Long userId,
        Pageable pageable
    );

    // 사용자 쿠폰 상태별 목록 조회
    // AVAILABLE / USED / EXPIRED 필터 조회 시 사용한다
    Page<UserCoupon> findAllByUserIdAndStatus(
        Long userId,
        UserCouponStatus status,
        Pageable pageable
    );

    // 주문 ID 기준 사용 쿠폰 조회
    // 주문 취소 시 사용된 쿠폰 복구에 사용한다
    Optional<UserCoupon> findByUsedOrderId(
        Long orderId
    );

}
