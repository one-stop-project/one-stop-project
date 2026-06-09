package com.sparta.one_stop.domain.coupon.repository;

import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

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
    @EntityGraph(attributePaths = "coupon")
    Optional<UserCoupon> findByIdAndUserId(
        Long userCouponId,
        Long userId
    );

    // 사용자 쿠폰 전체 목록 조회
    // 마이페이지 내 쿠폰 목록 조회 시 사용한다
    @EntityGraph(attributePaths = "coupon")
    Page<UserCoupon> findAllByUserId(
        Long userId,
        Pageable pageable
    );

    // 사용자 쿠폰 상태별 목록 조회
    // AVAILABLE / USED / EXPIRED 필터 조회 시 사용한다
    @EntityGraph(attributePaths = "coupon")
    Page<UserCoupon> findAllByUserIdAndStatus(
        Long userId,
        UserCouponStatus status,
        Pageable pageable
    );

    // 주문 ID 기준 사용 쿠폰 조회
    // 주문 취소 시 사용된 쿠폰 복구에 사용한다
    @EntityGraph(attributePaths = "coupon")
    Optional<UserCoupon> findByUsedOrderId(
        Long orderId
    );

    // 사용자 쿠폰 단건 조회 + 비관적 쓰기 락
    // 결제 승인 시점에 쿠폰 이중사용을 방지하기 위해 사용한다
    // 같은 UserCoupon을 서로 다른 주문에서 동시에 결제하려는 경우,
    // 먼저 조회한 트랜잭션이 락을 점유하고 이후 트랜잭션은 대기한다
    @EntityGraph(attributePaths = "coupon")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    })
    @Query("""
        select uc
        from UserCoupon uc
        where uc.id = :userCouponId
    """)
    Optional<UserCoupon> findByIdWithLock(@Param("userCouponId") Long userCouponId);

}
