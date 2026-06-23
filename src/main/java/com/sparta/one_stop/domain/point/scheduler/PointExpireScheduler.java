package com.sparta.one_stop.domain.point.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointExpireScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String LOCK_KEY = "point-expire-batch-lock";
    private static final long LOCK_WAIT_SECONDS = 0L;
    private static final long LOCK_LEASE_HOURS = 1L;
    private final JobLauncher jobLauncher;
    private final Job pointExpireJob;
    private final JobExplorer jobExplorer;
    private final RedissonClient redissonClient;
    @Value("${app.scheduler.enabled:false}")
    private boolean schedulerEnabled;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  주기 실행 — 매일 새벽 3시 (KST)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 매일 새벽 3시 (Asia/Seoul) — 그날 만료 대상 처리
     *
     * <p>cron 필드: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void runDailyExpiration() {
        // S2처럼 app.scheduler.enabled=false인 서버에서는 스케줄 실행 건너뜀
        if (!schedulerEnabled) {
            return;
        }

        // ── 1차 방어 — Redisson 분산 락 ──
        RLock lock = redissonClient.getLock(LOCK_KEY);

        try {
            // 대기 0초, 유지 1시간 (배치 최대 예상 시간)
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_HOURS, TimeUnit.HOURS)) {
                log.info("[POINT_EXPIRE] 다른 서버가 실행 중 — 락 획득 실패, 스킵");
                return;
            }

            // ── 2차 방어 — JobExplorer로 RUNNING 상태 재확인 ──
            int runningCount = jobExplorer.findRunningJobExecutions(pointExpireJob.getName()).size();
            if (runningCount > 0) {
                log.warn("[POINT_EXPIRE] 이미 실행 중인 Job 존재 — 중복 실행 방지 (running={})", runningCount);
                return;
            }

            // ── 실제 실행 ──
            LocalDate today = LocalDate.now(KST);
            log.info("[POINT_EXPIRE] 시작 — expireDate={}", today);

            JobExecution execution = launch(today);

            log.info("[POINT_EXPIRE] 종료 — status={}, read={}, write={}",
                execution.getStatus(),
                execution.getStepExecutions().stream().mapToLong(s -> s.getReadCount()).sum(),
                execution.getStepExecutions().stream().mapToLong(s -> s.getWriteCount()).sum());

        } catch (InterruptedException e) {
            log.error("[POINT_EXPIRE] 락 획득 중 인터럽트", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[POINT_EXPIRE] 실행 실패", e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  수동 실행 — Admin API용
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 수동 실행 — Admin이 특정 날짜로 만료 처리
     *
     * 주의: 자동 스케줄과 동일한 락 사용 → 동시 실행 방지
     *
     * @return 실행 결과 요약
     * @throws IllegalStateException 다른 실행이 진행 중일 때
     */
    public String runManually(LocalDate targetDate) {
        if (targetDate.isAfter(LocalDate.now(KST))) {
            throw new IllegalArgumentException(
                "targetDate는 오늘 또는 이전 날짜여야 합니다: " + targetDate
            );
        }

        log.info("[POINT_EXPIRE_MANUAL] 수동 실행 요청 — targetDate={}", targetDate);

        RLock lock = redissonClient.getLock(LOCK_KEY);

        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_HOURS, TimeUnit.HOURS)) {
                throw new IllegalStateException(
                    "포인트 만료 배치가 이미 실행 중입니다. 잠시 후 다시 시도해주세요.");
            }

            int runningCount = jobExplorer.findRunningJobExecutions(pointExpireJob.getName()).size();
            if (runningCount > 0) {
                throw new IllegalStateException(
                    "포인트 만료 Job이 이미 RUNNING 상태입니다.");
            }

            JobExecution execution = launch(targetDate);
            return String.format("status=%s, read=%d, write=%d",
                execution.getStatus(),
                execution.getStepExecutions().stream().mapToLong(s -> s.getReadCount()).sum(),
                execution.getStepExecutions().stream().mapToLong(s -> s.getWriteCount()).sum());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 획득 중 인터럽트 발생", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[POINT_EXPIRE_MANUAL] 수동 실행 실패 — targetDate={}", targetDate, e);
            throw new RuntimeException("수동 만료 실행 실패", e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  내부 — Job 실행
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private JobExecution launch(LocalDate expireDate) throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addString("expireDate", expireDate.toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();

        return jobLauncher.run(pointExpireJob, params);
    }
}
