package com.sparta.one_stop.dummy.product;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

// 더미 시드 주기 실행 — dummy.scheduler.enabled=true 일 때만 빈으로 뜸(운영은 끔).
// 매일 정해진 시각(cron) 1회 오케스트레이터 호출. 같은 인스턴스 내 중복 실행(오버랩)은 막는다.
@Component
@ConditionalOnProperty(prefix = "dummy.scheduler", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DummyProductScheduler {

    private static final Logger log = LoggerFactory.getLogger(DummyProductScheduler.class);

    // 네이버 키 조건부라 부재 가능 → ObjectProvider로 받아 없으면 건너뜀(스케줄러만 켜지고 키 없는 경우)
    private final ObjectProvider<DummyProductOrchestrator> orchestratorProvider;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(cron = "${dummy.scheduler.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void runDailySeed() {
        DummyProductOrchestrator orchestrator = orchestratorProvider.getIfAvailable();
        if (orchestrator == null) {
            log.warn("[더미시드] 스케줄러 ON이지만 네이버 키(naver.api.client-id) 미설정 → 건너뜀");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("[더미시드] 이전 실행이 아직 진행 중 → 이번 틱 건너뜀");
            return;
        }
        try {
            orchestrator.run();
        } catch (Exception e) {
            log.error("[더미시드] 실행 중 예외", e);
        } finally {
            running.set(false);
        }
    }
}
