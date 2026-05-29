package com.sparta.one_stop.domain.product.scheduler;

import com.sparta.one_stop.domain.product.event.SearchHistoryEvent;
import com.sparta.one_stop.domain.product.service.PopularKeywordService;
import com.sparta.one_stop.domain.product.service.SearchHistorySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// 5분마다 Redis LIST를 비우면서 DB에 batch INSERT
// 흐름: peek(batch) → syncBatch(@Transactional) → ack(LTRIM)
// syncBatch 실패 시 ack 미호출 → 다음 사이클 재시도 (유실 방지)
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchHistoryScheduler {

    private static final long FIVE_MINUTES_MS = 5L * 60 * 1000;
    private static final int BATCH_SIZE = 500;

    private final PopularKeywordService popularKeywordService;
    private final SearchHistorySyncService syncService;

    @Scheduled(fixedRate = FIVE_MINUTES_MS)
    public void sync() {
        List<SearchHistoryEvent> events;
        try {
            events = popularKeywordService.peekHistoryBatch(BATCH_SIZE);
        } catch (Exception e) {
            log.warn("[SearchHistory] peek failed: {}", e.getMessage());
            return;
        }
        if (events.isEmpty()) return;

        try {
            syncService.syncBatch(events);
        } catch (Exception e) {
            // 커밋 실패 → ack 안 함, 다음 사이클에서 같은 BATCH 재처리
            log.error("[SearchHistory] sync failed (batch={}), will retry next cycle", events.size(), e);
            return;
        }

        try {
            popularKeywordService.ackHistoryBatch(events.size());
            log.info("[SearchHistory] sync done (batch={})", events.size());
        } catch (Exception e) {
            // ack 실패해도 DB는 이미 INSERT 됨 — 중복 발생 가능, 다음 사이클에서 dedup 없이 처리
            log.error("[SearchHistory] ack failed (batch={})", events.size(), e);
        }
    }
}
