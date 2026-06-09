package com.sparta.one_stop.domain.product.scheduler;

import com.sparta.one_stop.domain.product.service.PopularKeywordService;
import com.sparta.one_stop.domain.product.service.SearchHistorySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 5분마다 Redis 큐에 쌓인 검색 로그를 모아서 DB에 한 번에 저장
// 흐름: 읽기 → 저장 → 저장 성공분만 큐에서 제거
// 저장 실패 시 큐를 안 비움 → 다음 사이클 재시도 (유실 방지)
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
        // 꺼낸 게 없을 때만 종료. 전부 깨졌어도 큐 앞을 비워야 거기서 안 막힘
        if (batch.rawCount() == 0) return;

        try {
            syncService.syncBatch(batch.events());
        } catch (Exception e) {
            // 저장 실패 → 큐 안 비움, 다음 사이클 재시도
            log.error("[SearchHistory] sync failed (rawCount={}), will retry next cycle", batch.rawCount(), e);
            return;
        }

        try {
            popularKeywordService.ackHistoryBatch(batch.rawCount());
            log.info("[SearchHistory] sync done (rawCount={}, inserted={})", batch.rawCount(), batch.events().size());
        } catch (Exception e) {
            // 큐 비우기 실패 — DB엔 이미 저장됨, 다음 사이클에 중복 가능
            log.error("[SearchHistory] ack failed (rawCount={})", batch.rawCount(), e);
        }
    }
}
