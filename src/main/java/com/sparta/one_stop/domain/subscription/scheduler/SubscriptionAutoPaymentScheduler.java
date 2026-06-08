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
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class SubscriptionAutoPaymentScheduler {

    private final SubscriptionSchedulerService subscriptionSchedulerService;

    /**
     * ?ë™ ê²°ì œ ?¤ì?ì¤„ëŸ¬
     * ë§¤ì¼ ?ì • ?¤í–‰
     * ACTIVE ?íƒœ?´ë©° nextPaymentDateê°€ ?¤ëŠ˜ ?´í•˜??êµ¬ë…??ì¡°íšŒ
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void processAutoPayment() {
        log.info("?ë™ê²°ì œ ?¤ì?ì¤„ëŸ¬ ?œì‘");
        subscriptionSchedulerService.processAutoPayment();
        log.info("?ë™ê²°ì œ ?¤ì?ì¤„ëŸ¬ ì¢…ë£Œ");
    }
}
