package com.sparta.one_stop.domain.point.repository;

import com.sparta.one_stop.domain.point.entity.PointHistory;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PointHistoryRepository extends JpaRepository<PointHistory, Long> {

    // 사용자 ID 기준 전체 포인트 이력 페이징 조회
    // type 필터 없이 마이페이지 포인트 이력 조회 시 사용한다
    Page<PointHistory> findAllByUserId(
        Long userId,
        Pageable pageable
    );

    // 사용자 ID + 이력 유형 기준 포인트 이력 페이징 조회
    // CHARGE / EARN / USE / REFUND / EXPIRE 타입 필터 조회 시 사용한다
    Page<PointHistory> findAllByUserIdAndType(
        Long userId,
        PointHistoryType type,
        Pageable pageable
    );

    // FEFO + FIFO 기준 차감 대상 포인트 이력 조회
    // 포인트 사용 시 remainingAmount가 남아 있고, 아직 만료되지 않은 CHARGE / EARN / REFUND 이력을 조회한다
    // 정렬 기준:
    // 1순위 expireAt ASC  - 만료일이 가까운 포인트 먼저 차감
    // 2순위 createdAt ASC - 만료일이 같으면 먼저 적립된 포인트 먼저 차감
    @Query("""
        select ph
        from PointHistory ph
        where ph.point.id = :pointId
          and ph.type in :types
          and ph.remainingAmount > 0
          and ph.expireAt >= :today
        order by ph.expireAt asc, ph.createdAt asc
    """)
    List<PointHistory> findDeductibleHistories(
        @Param("pointId") Long pointId,
        @Param("types") List<PointHistoryType> types,
        @Param("today") LocalDate today
    );

    // 만료 처리 대상 포인트 이력 조회
    // 정책: expireAt 당일은 23:59:59까지 사용 가능하므로 오늘보다 이전인 이력만 조회한다.
    // 조회된 이력은 remainingAmount를 0으로 변경하고 EXPIRE 이력을 생성하는 데 사용한다
    @Query("""
        select ph
        from PointHistory ph
        where ph.type in :types
          and ph.remainingAmount > 0
          and ph.expireAt < :today
        order by ph.expireAt asc, ph.createdAt asc
    """)
    List<PointHistory> findExpireTargetHistories(
        @Param("types") List<PointHistoryType> types,
        @Param("today") LocalDate today
    );

    // 30일 이내 만료 예정 포인트 금액 합산
    // 마이페이지에서 곧 만료될 포인트 금액을 보여주기 위해 사용한다
    @Query("""
        select coalesce(sum(ph.remainingAmount), 0)
        from PointHistory ph
        where ph.point.id = :pointId
          and ph.type in :types
          and ph.remainingAmount > 0
          and ph.expireAt between :today and :until
    """)
    Long sumExpiringSoonAmount(
        @Param("pointId") Long pointId,
        @Param("types") List<PointHistoryType> types,
        @Param("today") LocalDate today,
        @Param("until") LocalDate until
    );

    // 30일 이내 가장 가까운 포인트 만료일 조회
    // 마이페이지 expiringSoon 응답의 expireAt 값을 만들 때 사용한다
    @Query("""
        select min(ph.expireAt)
        from PointHistory ph
        where ph.point.id = :pointId
          and ph.type in :types
          and ph.remainingAmount > 0
          and ph.expireAt between :today and :until
    """)
    Optional<LocalDate> findNearestExpiringDate(
        @Param("pointId") Long pointId,
        @Param("types") List<PointHistoryType> types,
        @Param("today") LocalDate today,
        @Param("until") LocalDate until
    );

    // 주문 ID + 이력 유형 기준 포인트 이력 조회
    // 주문 취소 시 해당 주문에서 생성된 USE 이력을 찾아 PointUsageDetail 기반으로 복구할 때 사용한다
    List<PointHistory> findAllByOrderIdAndType(
        Long orderId,
        PointHistoryType type
    );

}
