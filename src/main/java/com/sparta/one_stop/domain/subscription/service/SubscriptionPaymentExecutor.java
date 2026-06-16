package com.sparta.one_stop.domain.subscription.service;

import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.domain.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPaymentExecutor {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * chunk 단위 자동결제 처리 (성공: renew / 실패: expire)
     */
    @Transactional
    public void processChunk(List<Long> subscriptionIds, LocalDateTime now) {
        List<Subscription> subscriptions =
            subscriptionRepository.findAllById(subscriptionIds);

        for (Subscription subscription : subscriptions) {
            try {
                subscription.renew(now);
                log.info("자동결제 성공 subscriptionId={}", subscription.getId());
            } catch (Exception e) {
                subscription.expire();
                log.error("자동결제 실패 subscriptionId={}", subscription.getId(), e);
            }
        }
    }
}
