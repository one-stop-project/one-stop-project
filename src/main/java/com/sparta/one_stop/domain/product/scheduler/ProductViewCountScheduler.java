package com.sparta.one_stop.domain.product.scheduler;

import com.sparta.one_stop.domain.product.service.ProductViewCountService;
import com.sparta.one_stop.domain.product.service.ProductViewCountSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

// 5분마다 Redis 누적 조회수를 MySQL에 동기화
// dirty SET에 등록된 productId만 처리 → 변경 없는 상품은 건드리지 않음
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewCountScheduler {

    private static final long FIVE_MINUTES_MS = 5L * 60 * 1000;

    private final ProductViewCountService viewCountService;
    private final ProductViewCountSyncService syncService;

    @Scheduled(fixedRate = FIVE_MINUTES_MS)
    public void sync() {
        Set<String> dirty = viewCountService.getDirtyProductIds();
        if (dirty.isEmpty()) {
            return;
        }

        log.info("[ViewCount] sync start (dirty size={})", dirty.size());
        int success = 0;
        int failure = 0;

        for (String pidStr : dirty) {
            try {
                Long productId = Long.valueOf(pidStr);
                syncService.syncOne(productId);
                success++;
            } catch (Exception e) {
                failure++;
                log.warn("[ViewCount] syncOne failed (productId={}): {}", pidStr, e.getMessage());
            }
        }

        log.info("[ViewCount] sync done (success={}, failure={})", success, failure);
    }
}
