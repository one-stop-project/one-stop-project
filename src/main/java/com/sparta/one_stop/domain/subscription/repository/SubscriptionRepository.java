package com.sparta.one_stop.domain.subscription.repository;

import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository
    extends JpaRepository<Subscription, Long> {

    /**
     * ACTIVE 또는 CANCELLED 존재 여부
     */
    boolean existsByUserIdAndStatusIn(
        Long userId,
        List<SubscriptionStatus> statuses
    );

    /**
     * 내 유효 구독 조회
     */
    Optional<Subscription> findTopByUserIdAndStatusInOrderByCreatedAtDesc(
        Long userId,
        List<SubscriptionStatus> statuses
    );

    /**
     * 자동 결제 (chunk 처리)
     */
    List<Subscription> findAllByStatusAndNextPaymentDateLessThanEqual(
        SubscriptionStatus status,
        LocalDateTime now,
        Pageable pageable
    );

    /**
     * 만료 처리 (chunk 처리)
     */
    List<Subscription> findAllByStatusAndEndAtBefore(
        SubscriptionStatus status,
        LocalDateTime now,
        Pageable pageable
    );

    /**
     * 자동 결제 대상 ID 조회 (트랜잭션 밖에서 사용)
     * 엔티티가 아닌 ID만 조회하여 detached 문제 방지
     */
    @Query("SELECT s.id FROM Subscription s WHERE s.status = :status AND s.nextPaymentDate <= :now ORDER BY s.id ASC")
    List<Long> findIdsByStatusAndNextPaymentDateLessThanEqual(
        @Param("status") SubscriptionStatus status,
        @Param("now") LocalDateTime now,
        Pageable pageable
    );

    long countByStatus(SubscriptionStatus status);

    @EntityGraph(attributePaths = "user")
    Page<Subscription> findAll(Pageable pageable);
}
