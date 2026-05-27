package com.sparta.one_stop.domain.product.scheduler;

import com.sparta.one_stop.domain.product.service.ProductViewCountService;
import com.sparta.one_stop.domain.product.service.ProductViewCountSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

// 5분마다 Redis 누적 조회수를 MySQL에 동기화
// 흐름: peek(count) → DB UPDATE 성공 → acknowledge(DECRBY)
// DB UPDATE 실패 시 acknowledge 미호출 → 다음 사이클에서 재시도 (유실 방지)
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
                long count = viewCountService.peekCount(productId);

                if (count <= 0) {
                    // counter는 비었는데 dirty에는 남아있는 경우 — dirty cleanup
                    viewCountService.acknowledge(productId, 0L);
                    continue;
                }

                // DB UPDATE 성공 후에만 acknowledge — 트랜잭션 커밋 성공이 전제
                syncService.syncOne(productId, count);
                viewCountService.acknowledge(productId, count);
                success++;
            } catch (Exception e) {
                failure++;
                log.warn("[ViewCount] syncOne failed (productId={}): {}", pidStr, e.getMessage());
            }
        }

        log.info("[ViewCount] sync done (success={}, failure={})", success, failure);
    }
}
