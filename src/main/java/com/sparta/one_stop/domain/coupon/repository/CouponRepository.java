package com.sparta.one_stop.domain.coupon.repository;

import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
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
    // 선착순 발급 성공 후 DB 기준 실제 발급 완료 수량을 원자적으로 증가시킨다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Coupon c
        set c.issuedQuantity = c.issuedQuantity + 1
        where c.id = :couponId
          and c.issuedQuantity < c.totalQuantity
    """)
    int increaseIssuedQuantity(
        @Param("couponId") Long couponId
    );

}
