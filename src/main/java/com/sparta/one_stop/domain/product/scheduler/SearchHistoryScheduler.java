package com.sparta.one_stop.domain.product.scheduler;

import com.sparta.one_stop.domain.product.service.PopularKeywordService;
import com.sparta.one_stop.domain.product.service.SearchHistorySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 5ë¶„ë§ˆ??Redis ?ì— ?“ì¸ ê²€??ë¡œê·¸ë¥?ëª¨ì•„??DB????ë²ˆì— ?€??
// ?ë¦„: ?½ê¸° ???€?????€???±ê³µë¶„ë§Œ ?ì—???œê±°
// ?€???¤íŒ¨ ???ë? ??ë¹„ì? ???¤ìŒ ?¬ì´???¬ì‹œ??(? ì‹¤ ë°©ì?)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class SearchHistoryScheduler {

    private static final long FIVE_MINUTES_MS = 5L * 60 * 1000;
    private static final int BATCH_SIZE = 500;

    private final PopularKeywordService popularKeywordService;
    private final SearchHistorySyncService syncService;

    @Scheduled(fixedRate = FIVE_MINUTES_MS)
    public void sync() {
        PopularKeywordService.HistoryBatch batch;
        try {
            batch = popularKeywordService.peekHistoryBatch(BATCH_SIZE);
        } catch (Exception e) {
            log.warn("[SearchHistory] peek failed: {}", e.getMessage());
            return;
        }
        // êº¼ë‚¸ ê²??†ì„ ?Œë§Œ ì¢…ë£Œ. ?„ë? ê¹¨ì¡Œ?´ë„ ???ì„ ë¹„ì›Œ??ê±°ê¸°????ë§‰í˜
        if (batch.rawCount() == 0) return;

        try {
            syncService.syncBatch(batch.events());
        } catch (Exception e) {
            // ?€???¤íŒ¨ ??????ë¹„ì?, ?¤ìŒ ?¬ì´???¬ì‹œ??
            log.error("[SearchHistory] sync failed (rawCount={}), will retry next cycle", batch.rawCount(), e);
            return;
        }

        try {
            popularKeywordService.ackHistoryBatch(batch.rawCount());
            log.info("[SearchHistory] sync done (rawCount={}, inserted={})", batch.rawCount(), batch.events().size());
        } catch (Exception e) {
            // ??ë¹„ìš°ê¸??¤íŒ¨ ??DB???´ë? ?€?¥ë¨, ?¤ìŒ ?¬ì´?´ì— ì¤‘ë³µ ê°€??
            log.error("[SearchHistory] ack failed (rawCount={})", batch.rawCount(), e);
        }
    }
}
