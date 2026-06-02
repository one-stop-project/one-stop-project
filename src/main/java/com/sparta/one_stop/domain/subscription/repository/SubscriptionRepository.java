package com.sparta.one_stop.domain.subscription.repository;

import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository
    extends JpaRepository<Subscription, Long> {

    /**
     * 내 최신 구독 조회
     */
    Optional<Subscription> findTopByUserIdOrderByCreatedAtDesc(
        Long userId
    );

    /**
     * ACTIVE 또는 CANCELLED 존재 여부
     */
    boolean existsByUserIdAndStatusIn(
        Long userId,
        List<SubscriptionStatus> statuses
    );

    /**
     * 만료 스케줄러용
     */
    List<Subscription> findAllByStatusAndEndAtBefore(
        SubscriptionStatus status,
        LocalDateTime now
    );

    Optional<Subscription> findByUserIdAndStatusIn(
        Long userId,
        List<SubscriptionStatus> statuses
    );
}
