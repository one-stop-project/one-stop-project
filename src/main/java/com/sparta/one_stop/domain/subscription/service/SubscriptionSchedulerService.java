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
public class SubscriptionSchedulerService {

    private static final int CHUNK_SIZE = 1000;

    private final SubscriptionRepository subscriptionRepository;

    /**
     * 자동 결제 처리
     *
     * - ACTIVE + nextPaymentDate <= now 대상
     * - chunk(1000개) 단위로 반복 처리
     * - Mock 결제 기반 (추후 PG 연동 가능)
     *   - 성공: renew()
     *   - 실패: expire()
     */
    @Transactional
    public void processAutoPayment() {
        LocalDateTime now = LocalDateTime.now();
        while (true) {
            List<Subscription> subscriptions =
                subscriptionRepository.findAllByStatusAndNextPaymentDateLessThanEqual(
                    SubscriptionStatus.ACTIVE,
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
     * 개별 구독 자동 결제 처리 (renew / expire)
     */
    private void processSubscriptions(List<Subscription> subscriptions) {
        for (Subscription subscription : subscriptions) {
            try {
                subscription.renew();
                log.info(
                    "자동결제 성공 subscriptionId={}",
                    subscription.getId()
                );
            } catch (Exception e) {
                subscription.expire();
                log.error(
                    "자동결제 실패 subscriptionId={}",
                    subscription.getId(),
                    e
                );
            }
        }
    }
}
