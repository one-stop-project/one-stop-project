package com.sparta.one_stop.domain.product.scheduler;

import com.sparta.one_stop.domain.product.service.ProductViewCountService;
import com.sparta.one_stop.domain.product.service.ProductViewCountSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

// 5Î∂ÑÎßà??Redis ?ÑÏ†Å Ï°∞Ìöå?òÎ? MySQL???ôÍ∏∞??
// ?êÎ¶Ñ: ?ΩÍ∏∞ ??DB Î∞òÏòÅ ?±Í≥µ ??Î∞òÏòÅÎ∂ÑÎßå Redis?êÏÑú Ï∞®Í∞ê
// DB Î∞òÏòÅ ?§Ìå® ??Ï∞®Í∞ê ???????§Ïùå ?¨Ïù¥???¨Ïãú??(?†Ïã§ Î∞©Ï?)
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
                    // Ïπ¥Ïö¥?∞Îäî ÎπÑÏóà?îÎç∞ ?ôÍ∏∞???Ä??Î™©Î°ù?êÎßå ?®Ï? Í≤ΩÏö∞ ??Î™©Î°ù?êÏÑú ?úÍ±∞
                    viewCountService.acknowledge(productId, 0L);
                    continue;
                }

                // DB Î∞òÏòÅ ?±Í≥µ(Ïª§Î∞ã ?ÑÎ£å) ?ÑÏóêÎß?Redis Ï∞®Í∞ê
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

    // Îß§Ï£º ?îÏöî??00:00 (KST) ??product.view_count ?ºÍ¥Ñ 0 Ï¥àÍ∏∞??
    // Redis???®Ï? Ïπ¥Ïö¥?∞Îäî Í±¥ÎìúÎ¶¨Ï? ?äÏùå ???ÑÏßÅ Î∞òÏòÅ ????Î∂ÑÏ? ?§Ïùå Ï£ºÏ∞®Î°??¥Ïñ¥ ÏßëÍ≥Ñ??
    @Scheduled(cron = "0 0 0 ? * MON", zone = "Asia/Seoul")
    public void weeklyReset() {
        int reset = syncService.resetAllViewCounts();
        log.info("[ViewCount] weekly reset done (rows={})", reset);
    }
}
