package com.sparta.one_stop.domain.point.scheduler;

import com.sparta.one_stop.domain.point.service.PointExpireService;
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
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.explore.JobExplorer;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointExpireScheduler {

    private final JobLauncher jobLauncher;
    private final Job pointExpireJob;
    private final JobExplorer jobExplorer; // 🚀 현재 실행 중인 배치를 확인하기 위해 추가
    private final RedissonClient redissonClient; // 🚀 멀티 서버 동시성 제어를 위해 추가

    @Scheduled(cron = "0 0 3 * * *")
    public void runPointExpireBatch() {
        // 1. Redisson 분산 락 획득 시도 (동일한 시간에 1대의 서버만 실행되도록 보장)
        RLock lock = redissonClient.getLock("point-expire-batch-lock");

        try {
            // 락 획득 대기 시간 0초, 락 유지 시간 1시간 (배치 최대 예상 시간)
            if (!lock.tryLock(0, 1, TimeUnit.HOURS)) {
                log.info("다른 서버에서 이미 포인트 만료 배치가 실행 중입니다. (Lock 획득 실패)");
                return;
            }

            // 2. JobExplorer를 통한 심층 방어 (누군가 수동으로 돌리고 있을 때 스케줄러가 도는 것을 방지)
            int runningExecutions = jobExplorer.findRunningJobExecutions(pointExpireJob.getName()).size();
            if (runningExecutions > 0) {
                log.warn("이미 실행 중인 포인트 만료 배치가 존재합니다. 중복 실행을 방지합니다.");
                return;
            }

            log.info("포인트 만료 배치를 시작합니다.");

            // 3. 파라미터 세팅 (수동 재실행을 위해 timestamp는 유지하되, 위의 1,2번 로직으로 동시성을 완벽 제어함)
            LocalDate today = LocalDate.now();
            JobParameters params = new JobParametersBuilder()
                .addString("expireDate", today.toString())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

            jobLauncher.run(pointExpireJob, params);

        } catch (InterruptedException e) {
            log.error("배치 락 획득 중 인터럽트 발생", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("포인트 만료 배치 실행 중 오류 발생", e);
        } finally {
            // 배치 스케줄러 호출이 끝났을 때 락 해제 로직 (원한다면 배치 내부 로직이 끝날 때까지 유지하도록 세팅도 가능)
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
