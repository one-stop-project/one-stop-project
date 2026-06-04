package com.sparta.one_stop.domain.point.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 포인트 만료 스케줄러 — Spring Batch Job 실행 트리거
 *
 * <p><b>변경 사항</b>: 기존 {@code PointExpireScheduler}를 대체.
 * 직접 처리하던 로직을 {@code pointExpireJob} 실행으로 위임.
 *
 * <p><b>실행 주기</b>: 매월 1일 새벽 3시 (운영 트래픽 최소 시간대)
 * <br>cron: 초 분 시 일 월 요일
 *
 * <p><b>운영 고려사항</b>
 * <ul>
 *   <li>다중 인스턴스 환경에선 Shedlock 등 분산 락 필요 (한 인스턴스만 실행)</li>
 *   <li>JobParameter에 timestamp 포함 → 같은 날 재실행도 가능</li>
 *   <li>실패 시 알림 (Slack/PagerDuty) 연동 필요</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointExpireBatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job pointExpireJob;

    @Scheduled(cron = "0 0 3 1 * *", zone = "Asia/Seoul")
    public void runMonthlyExpiration() {
        LocalDate today = LocalDate.now();
        log.info("[POINT_EXPIRE_BATCH] 시작 — expireDate={}", today);

        try {
            JobParameters params = new JobParametersBuilder()
                .addString("expireDate", today.toString())
                .addLong("timestamp", System.currentTimeMillis())  // 재실행 가능
                .toJobParameters();

            var execution = jobLauncher.run(pointExpireJob, params);

            log.info("[POINT_EXPIRE_BATCH] 종료 — status={}, exitCode={}",
                execution.getStatus(), execution.getExitStatus().getExitCode());
        } catch (Exception e) {
            log.error("[POINT_EXPIRE_BATCH] 실행 실패", e);
            // TODO: 운영 알림 (Slack/PagerDuty)
            throw new RuntimeException("포인트 만료 배치 실행 실패", e);
        }
    }

    /**
     * 수동 실행용 — Admin API에서 호출 가능
     *
     * @return 처리된 결과 요약
     */
    public String runManually(LocalDate targetDate) {
        log.info("[POINT_EXPIRE_BATCH] 수동 실행 — targetDate={}", targetDate);
        try {
            JobParameters params = new JobParametersBuilder()
                .addString("expireDate", targetDate.toString())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            var execution = jobLauncher.run(pointExpireJob, params);
            return String.format("status=%s, readCount=%d, writeCount=%d",
                execution.getStatus(),
                execution.getStepExecutions().stream().mapToLong(s -> s.getReadCount()).sum(),
                execution.getStepExecutions().stream().mapToLong(s -> s.getWriteCount()).sum());
        } catch (Exception e) {
            throw new RuntimeException("수동 만료 실행 실패", e);
        }
    }
}

