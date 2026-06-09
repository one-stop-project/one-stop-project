package com.sparta.one_stop.domain.product.scheduler;

import com.sparta.one_stop.domain.product.service.PopularProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 인기 상품 ZSET 5분마다 갱신
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class PopularProductScheduler {

    private static final long FIVE_MINUTES_MS = 5L * 60 * 1000;

    private final PopularProductService popularProductService;

    @Scheduled(fixedRate = FIVE_MINUTES_MS)
    public void refresh() {
        popularProductService.refresh();
    }
}
