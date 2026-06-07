package com.sparta.one_stop.domain.subscription.service;

import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.domain.subscription.repository.SubscriptionRepository;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionBenefitService {

    private final SubscriptionRepository subscriptionRepository;

    private static final double DISCOUNT_RATE = 0.05;

    /**
     * 구독 할인 금액 계산
     */
    public Long calculateDiscount(Long userId, Long totalPrice) {
        Subscription subscription = subscriptionRepository
            .findTopByUserIdAndStatusInOrderByCreatedAtDesc(
                userId,
                List.of(
                    SubscriptionStatus.ACTIVE,
                    SubscriptionStatus.CANCELLED
                )
            )
            .orElse(null);

        if (subscription == null) {
            return 0L;
        }
        if (!isValid(subscription)) {
            return 0L;
        }
        return (long) (totalPrice * DISCOUNT_RATE);
    }

    /**
     * 구독 혜택 가능 여부 판단
     */
    private boolean isValid(Subscription subscription) {
        SubscriptionStatus status = subscription.getStatus();

        // 1. ACTIVE는 무조건 OK
        if (status == SubscriptionStatus.ACTIVE) {
            return true;
        }

        // 2. CANCELLED는 endAt 기준으로 판단
        if (status == SubscriptionStatus.CANCELLED) {
            return subscription.getEndAt().isAfter(LocalDateTime.now());
        }

        // 3. EXPIRED는 무조건 제외
        return false;
    }
}
