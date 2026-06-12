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
     * - 처리된 행은 expire()로 CANCELLED 조건에서 빠지므로 항상 page 0을 조회한다.
     *   (page++ 사용 시 offset이 밀려 행을 건너뛰는 버그가 있었다.)
     * - 단, expire()가 계속 실패하는 행이 있으면 page 0에 영원히 남아 무한루프가
     *   될 수 있으므로, 한 청크에서 한 건도 처리하지 못하면 루프를 중단한다.
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

            int processed = processSubscriptions(subscriptions);
            if (processed == 0) {
                log.error(
                    "구독 만료 처리 진행 없음 - 루프 중단 size={}",
                    subscriptions.size()
                );
                break;
            }
        }
    }

    /**
     * 개별 만료 처리 (멱등성 보장)
     * - 성공적으로 처리한 건수를 반환해 진행 여부를 판단한다.
     */
    private int processSubscriptions(List<Subscription> subscriptions) {
        int processed = 0;
        for (Subscription subscription : subscriptions) {
            try {
                subscription.expire();
                processed++;
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
        return processed;
    }
}
