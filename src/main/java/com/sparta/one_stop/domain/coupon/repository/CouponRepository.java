package com.sparta.one_stop.domain.coupon.repository;

import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // 발급 가능 쿠폰 목록 조회
    // ACTIVE 상태이고, 현재 시각이 발급/사용 기간 안에 있으며, 발급 수량이 남아 있는 쿠폰을 조회한다
    @Query("""
        select c
        from Coupon c
        where c.status = :status
          and c.startAt <= :now
          and c.expiredAt >= :now
          and c.issuedQuantity < c.totalQuantity
        order by c.createdAt desc
    """)
    List<Coupon> findAvailableCoupons(
        @Param("status") CouponStatus status,
        @Param("now") LocalDateTime now
    );

    // 쿠폰 발급 완료 수량 증가
    // validateIssuable() 이후 쿠폰 상태가 변경될 수 있으므로
    // UPDATE 시점에 ACTIVE 상태와 잔여 수량을 함께 검증한다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Coupon c
        set c.issuedQuantity = c.issuedQuantity + 1
        where c.id = :couponId
          and c.status = :status
          and c.issuedQuantity < c.totalQuantity
    """)
    int increaseIssuedQuantity(
        @Param("couponId") Long couponId,
        @Param("status") CouponStatus status
    );

    // 관리자 전체 목록 조회 (최신순)
    Page<Coupon> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 관리자 상태별 목록 조회 (최신순)
    Page<Coupon> findAllByStatusOrderByCreatedAtDesc(CouponStatus status, Pageable pageable);

    // 만료 기간이 지난 ACTIVE 쿠폰 일괄 비활성화 (ACTIVE → INACTIVE)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Coupon c
        set c.status = :inactiveStatus
        where c.status = :activeStatus
          and c.expiredAt < :now
    """)
    int bulkDeactivateExpired(
        @Param("activeStatus") CouponStatus activeStatus,
        @Param("inactiveStatus") CouponStatus inactiveStatus,
        @Param("now") LocalDateTime now
    );

}
