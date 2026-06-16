package com.sparta.one_stop.domain.subscription.service;

import com.sparta.one_stop.domain.subscription.repository.SubscriptionRepository;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionSchedulerService {

    private static final int CHUNK_SIZE = 1000;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentExecutor paymentExecutor;

    /**
     * 자동 결제 처리
     * <p>
     * - ACTIVE + nextPaymentDate <= now 대상
     * - chunk(1000개) 단위로 반복 처리
     * - Mock 결제 기반 (추후 PG 연동 가능)
     * - 성공: renew()
     * - 실패: expire()
     */
    public void processAutoPayment() {
        LocalDateTime now = LocalDateTime.now();
        while (true) {
            List<Long> subscriptionIds =
                subscriptionRepository.findIdsByStatusAndNextPaymentDateLessThanEqual(
                    SubscriptionStatus.ACTIVE,
                    now,
                    PageRequest.of(0, CHUNK_SIZE, Sort.by("id").ascending())
                );
            if (subscriptionIds.isEmpty()) {
                break;
            }
            paymentExecutor.processChunk(subscriptionIds, now);
        }
    }
}
