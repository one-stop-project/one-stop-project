package com.sparta.one_stop.domain.subscription.scheduler;

import com.sparta.one_stop.domain.subscription.service.SubscriptionSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionAutoPaymentScheduler {

    private final SubscriptionSchedulerService subscriptionSchedulerService;

    /**
     * 자동 결제 스케줄러
     * 매일 자정 실행
     * ACTIVE 상태이며 nextPaymentDate가 오늘 이하인 구독을 조회
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void processAutoPayment() {
        log.info("자동결제 스케줄러 시작");
        subscriptionSchedulerService.processAutoPayment();
        log.info("자동결제 스케줄러 종료");
    }
}
