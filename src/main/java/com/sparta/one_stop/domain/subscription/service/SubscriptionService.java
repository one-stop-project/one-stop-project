package com.sparta.one_stop.domain.subscription.service;

import com.sparta.one_stop.domain.subscription.dto.request.CancelSubscriptionRequest;
import com.sparta.one_stop.domain.subscription.dto.response.SubscriptionResponse;
import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.domain.subscription.repository.SubscriptionRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    /**
     * 구독 시작
     */
    public SubscriptionResponse subscribe(Long userId) {

        // 비관적 락 — 같은 유저의 동시 구독 신청 방지
        User user = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));

        boolean exists = subscriptionRepository.existsByUserIdAndStatusIn(
            userId,
            List.of(
                SubscriptionStatus.ACTIVE,
                SubscriptionStatus.CANCELLED
            )
        );

        if (exists) {
            throw new CustomException(ErrorCode.SUBSCRIPTION_002);
        }

        LocalDateTime now = LocalDateTime.now();

        Subscription subscription = Subscription.builder()
            .user(user)
            .startAt(now)
            .endAt(now.plusDays(30))
            .nextPaymentDate(now.plusDays(30))
            .build();

        Subscription saved = subscriptionRepository.save(subscription);

        log.info("구독 생성: userId={}, subscriptionId={}", userId, saved.getId());

        return SubscriptionResponse.from(saved);
    }

    /**
     * 내 구독 조회
     */
    @Transactional(readOnly = true)
    public SubscriptionResponse getMySubscription(Long userId) {

        Subscription subscription = findSubscription(userId);

        return SubscriptionResponse.from(subscription);
    }

    /**
     * 구독 해지
     */
    public SubscriptionResponse cancelSubscription(
        Long userId,
        CancelSubscriptionRequest request
    ) {

        Subscription subscription = subscriptionRepository
            .findTopByUserIdAndStatusInOrderByCreatedAtDesc(
                userId,
                List.of(
                    SubscriptionStatus.ACTIVE,
                    SubscriptionStatus.CANCELLED,
                    SubscriptionStatus.EXPIRED
                )
            )
            .orElseThrow(() -> new CustomException(ErrorCode.SUBSCRIPTION_001));

        subscription.cancel(request.reason());

        log.info("구독 해지: userId={}, subscriptionId={}", userId, subscription.getId());

        return SubscriptionResponse.from(subscription);
    }

    /**
     * 활성 구독 여부
     */
    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(Long userId) {

        return subscriptionRepository.existsByUserIdAndStatusIn(
            userId,
            List.of(SubscriptionStatus.ACTIVE)
        );
    }

    private Subscription findSubscription(Long userId) {
        return subscriptionRepository
            .findTopByUserIdAndStatusInOrderByCreatedAtDesc(
                userId,
                List.of(
                    SubscriptionStatus.ACTIVE,
                    SubscriptionStatus.CANCELLED,
                    SubscriptionStatus.EXPIRED
                )
            )
            .orElseThrow(() -> new CustomException(ErrorCode.SUBSCRIPTION_001));
    }
}
