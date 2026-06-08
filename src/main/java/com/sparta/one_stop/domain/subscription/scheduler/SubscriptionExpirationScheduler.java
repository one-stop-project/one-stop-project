package com.sparta.one_stop.domain.subscription.scheduler;

import com.sparta.one_stop.domain.subscription.service.SubscriptionExpirationSchedulerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class SubscriptionExpirationScheduler {
    private final SubscriptionExpirationSchedulerService expirationSchedulerService;

    /**
     * 구독 만료 ?��?줄러
     * 매일 ?�정 ?�행
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void processExpiration() {
        log.info("구독 만료 ?��?줄러 ?�작");
        expirationSchedulerService.processExpiration();
        log.info("구독 만료 ?��?줄러 종료");
    }
}
