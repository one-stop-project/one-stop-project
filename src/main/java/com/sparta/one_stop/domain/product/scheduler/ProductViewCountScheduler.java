package com.sparta.one_stop.domain.product.scheduler;

import com.sparta.one_stop.domain.product.service.ProductViewCountService;
import com.sparta.one_stop.domain.product.service.ProductViewCountSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

// 5분마다 Redis 누적 조회수를 MySQL에 동기화
// 흐름: 읽기 → DB 반영 성공 → 반영분만 Redis에서 차감
// DB 반영 실패 시 차감 안 함 → 다음 사이클 재시도 (유실 방지)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = false)
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
                    // 카운터는 비었는데 동기화 대상 목록에만 남은 경우 — 목록에서 제거
                    viewCountService.acknowledge(productId, 0L);
                    continue;
                }

                // DB 반영 성공(커밋 완료) 후에만 Redis 차감
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

    // 매주 월요일 00:00 (KST) — Redis 잔여 카운터·dirty 셋 정리 후 product.view_count 일괄 0 초기화
    // Redis를 먼저 비워야 동시에 도는 sync가 잔여 카운트를 DB로 되돌리는 창을 줄임
    // (Redis를 안 비우면 다음 sync가 잔여 카운트를 되돌려 리셋이 무효화됨)
    @Scheduled(cron = "0 0 0 ? * MON", zone = "Asia/Seoul")
    public void weeklyReset() {
        long cleared = viewCountService.clearAllCounters();
        int reset = syncService.resetAllViewCounts();
        log.info("[ViewCount] weekly reset done (redisCleared={}, rows={})", cleared, reset);
    }
}
