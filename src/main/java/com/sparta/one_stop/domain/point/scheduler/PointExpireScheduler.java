package com.sparta.one_stop.domain.point.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = false)
public class PointExpireScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String LOCK_KEY = "point-expire-batch-lock";
    private static final long LOCK_WAIT_SECONDS = 0L;
    private static final long LOCK_LEASE_HOURS = 1L;

    private final JobLauncher jobLauncher;
    private final Job pointExpireJob;
    private final JobExplorer jobExplorer;
    private final RedissonClient redissonClient;

    // ?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”
    //  ì£¼ê¸° ?¤í–‰ ??ë§¤ì¼ ?ˆë²½ 3??(KST)
    // ?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”

    /**
     * ë§¤ì¼ ?ˆë²½ 3??(Asia/Seoul) ??ê·¸ë‚  ë§Œë£Œ ?€??ì²˜ë¦¬
     *
     * <p>cron ?„ë“œ: ì´?ë¶????????”ì¼
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void runDailyExpiration() {
        // ?€?€ 1ì°?ë°©ì–´ ??Redisson ë¶„ì‚° ???€?€
        RLock lock = redissonClient.getLock(LOCK_KEY);

        try {
            // ?€ê¸?0ì´? ? ì? 1?œê°„ (ë°°ì¹˜ ìµœë? ?ˆìƒ ?œê°„)
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_HOURS, TimeUnit.HOURS)) {
                log.info("[POINT_EXPIRE] ?¤ë¥¸ ?œë²„ê°€ ?¤í–‰ ì¤??????ë“ ?¤íŒ¨, ?¤í‚µ");
                return;
            }

            // ?€?€ 2ì°?ë°©ì–´ ??JobExplorerë¡?RUNNING ?íƒœ ?¬í™•???€?€
            int runningCount = jobExplorer.findRunningJobExecutions(pointExpireJob.getName()).size();
            if (runningCount > 0) {
                log.warn("[POINT_EXPIRE] ?´ë? ?¤í–‰ ì¤‘ì¸ Job ì¡´ì¬ ??ì¤‘ë³µ ?¤í–‰ ë°©ì? (running={})", runningCount);
                return;
            }

            // ?€?€ ?¤ì œ ?¤í–‰ ?€?€
            LocalDate today = LocalDate.now(KST);  // ??KST ëª…ì‹œ
            log.info("[POINT_EXPIRE] ?œì‘ ??expireDate={}", today);

            JobExecution execution = launch(today);

            log.info("[POINT_EXPIRE] ì¢…ë£Œ ??status={}, read={}, write={}",
                execution.getStatus(),
                execution.getStepExecutions().stream().mapToLong(s -> s.getReadCount()).sum(),
                execution.getStepExecutions().stream().mapToLong(s -> s.getWriteCount()).sum());

        } catch (InterruptedException e) {
            log.error("[POINT_EXPIRE] ???ë“ ì¤??¸í„°?½íŠ¸", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[POINT_EXPIRE] ?¤í–‰ ?¤íŒ¨", e);
            // TODO: Slack/PagerDuty ?Œë¦¼ ?°ë™ (ë³„ë„ PR ??issue #XXX)
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”
    //  ?˜ë™ ?¤í–‰ ??Admin API??
    // ?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”

    /**
     * ?˜ë™ ?¤í–‰ ??Admin???¹ì • ? ì§œë¡?ë§Œë£Œ ì²˜ë¦¬
     *
     * <p>ì£¼ì˜: ?ë™ ?¤ì?ì¤„ê³¼ ?™ì¼?????¬ìš© ???™ì‹œ ?¤í–‰ ë°©ì?
     *
     * @return ?¤í–‰ ê²°ê³¼ ?”ì•½
     * @throws IllegalStateException ?¤ë¥¸ ?¤í–‰??ì§„í–‰ ì¤‘ì¼ ??
     */
    public String runManually(LocalDate targetDate) {
        if (targetDate.isAfter(LocalDate.now(KST))) {
            throw new IllegalArgumentException(
                "targetDate???¤ëŠ˜ ?´ì „ ? ì§œ?¬ì•¼ ?©ë‹ˆ??" + targetDate
            );
        }

        log.info("[POINT_EXPIRE_MANUAL] ?˜ë™ ?¤í–‰ ?”ì²­ ??targetDate={}", targetDate);

        RLock lock = redissonClient.getLock(LOCK_KEY);

        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_HOURS, TimeUnit.HOURS)) {
                throw new IllegalStateException(
                    "?¬ì¸??ë§Œë£Œ ë°°ì¹˜ê°€ ?´ë? ?¤í–‰ ì¤‘ì…?ˆë‹¤. ? ì‹œ ???¤ì‹œ ?œë„?´ì£¼?¸ìš”.");
            }

            int runningCount = jobExplorer.findRunningJobExecutions(pointExpireJob.getName()).size();
            if (runningCount > 0) {
                throw new IllegalStateException(
                    "?¬ì¸??ë§Œë£Œ Job???´ë? RUNNING ?íƒœ?…ë‹ˆ??");
            }

            JobExecution execution = launch(targetDate);
            return String.format("status=%s, read=%d, write=%d",
                execution.getStatus(),
                execution.getStepExecutions().stream().mapToLong(s -> s.getReadCount()).sum(),
                execution.getStepExecutions().stream().mapToLong(s -> s.getWriteCount()).sum());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("???ë“ ì¤??¸í„°?½íŠ¸ ë°œìƒ", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("[POINT_EXPIRE_MANUAL] ?˜ë™ ?¤í–‰ ?¤íŒ¨ ??targetDate={}", targetDate, e);
            throw new RuntimeException("?˜ë™ ë§Œë£Œ ?¤í–‰ ?¤íŒ¨", e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”
    //  ?´ë? ??Job ?¤í–‰
    // ?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”?â”

    private JobExecution launch(LocalDate expireDate) throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addString("expireDate", expireDate.toString())
            .addLong("timestamp", System.currentTimeMillis())  // ?¬ì‹¤??ê°€??(ë¶„ì‚° ?½ì´ ?™ì‹œ??ë°©ì–´)
            .toJobParameters();

        return jobLauncher.run(pointExpireJob, params);
    }
}
