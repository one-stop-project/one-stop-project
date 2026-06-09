package com.sparta.one_stop.domain.product.scheduler;

import com.sparta.one_stop.domain.product.service.PopularKeywordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class PopularKeywordScheduler {

    private static final long FIVE_MINUTES_MS = 5L * 60 * 1000;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PopularKeywordService popularKeywordService;

    @Scheduled(fixedRate = FIVE_MINUTES_MS)
    public void refresh() {
        popularKeywordService.refresh();
    }

    // 자정 직후 어제자 final 보존 — 관리자 date 조회용
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void midnightRotate() {
        LocalDate yesterday = LocalDate.now(KST).minusDays(1);
        popularKeywordService.archiveAndReset(yesterday);
    }
}
