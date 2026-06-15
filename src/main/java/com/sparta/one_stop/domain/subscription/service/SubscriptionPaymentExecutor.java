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
            // 같은 실행에서 이미 갱신된 구독 스킵 (다중 결제 방지)
            if (subscription.getNextPaymentDate().isAfter(now)) {
                log.info("이미 갱신된 구독 스킵 subscriptionId={}", subscription.getId());
                continue;
            }

            try {
                subscription.renew();
                log.info("자동결제 성공 subscriptionId={}", subscription.getId());
            } catch (Exception e) {
                subscription.expire();
                log.error("자동결제 실패 subscriptionId={}", subscription.getId(), e);
            }
        }
    }
}
