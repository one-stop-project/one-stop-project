package com.sparta.one_stop.domain.subscription.service;

import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.domain.subscription.repository.SubscriptionRepository;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionExpirationSchedulerService {

    private static final int CHUNK_SIZE = 1000;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * 구독 만료 처리
     * - CANCELLED 상태 + endAt 지난 구독 → EXPIRED 처리
     * - chunk 기반 paging 처리 (page 증가 방식)
     */
    @Transactional
    public void processExpiration() {

        LocalDateTime now = LocalDateTime.now();
        while (true) {
            List<Subscription> subscriptions =
                subscriptionRepository.findAllByStatusAndEndAtBefore(
                    SubscriptionStatus.CANCELLED,
                    now,
                    PageRequest.of(0, CHUNK_SIZE, Sort.by("id").ascending())
                );
            if (subscriptions.isEmpty()) {
                break;
            }
            processSubscriptions(subscriptions);
        }
    }

    /**
     * 개별 만료 처리 (멱등성 보장)
     */
    private void processSubscriptions(List<Subscription> subscriptions) {
        for (Subscription subscription : subscriptions) {
            try {
                subscription.expire();
                log.info(
                    "구독 만료 처리 subscriptionId={}",
                    subscription.getId()
                );
            } catch (Exception e) {
                log.error(
                    "구독 만료 실패 subscriptionId={}",
                    subscription.getId(),
                    e
                );
            }
        }
    }
}
