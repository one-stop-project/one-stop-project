package com.sparta.one_stop.global.audit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Slf4j @Configuration @EnableAsync
public class SecurityAuditAsyncConfig {
    private static final long DROP_LOG_INTERVAL_MILLIS = 60_000L;

    @Bean(name="securityAuditExecutor")
    public Executor securityAuditExecutor(MeterRegistry meterRegistry){
        ThreadPoolTaskExecutor e=new ThreadPoolTaskExecutor(); e.setCorePoolSize(2); e.setMaxPoolSize(4);
        e.setQueueCapacity(1000); e.setThreadNamePrefix("security-audit-");
        Counter droppedCounter = Counter.builder("security.audit.dropped")
            .description("Number of security audit events dropped because the executor queue was full")
            .register(meterRegistry);
        LongAdder droppedSinceLastLog = new LongAdder();
        AtomicLong nextLogAt = new AtomicLong(0L);
        e.setRejectedExecutionHandler((r,x)->{
            droppedCounter.increment();
            droppedSinceLastLog.increment();
            long now = System.currentTimeMillis();
            long next = nextLogAt.get();
            if (now >= next && nextLogAt.compareAndSet(next, now + DROP_LOG_INTERVAL_MILLIS)) {
                log.warn(
                    "[SECURITY_AUDIT_DROPPED] audit queue is full, droppedSinceLastLog={}",
                    droppedSinceLastLog.sumThenReset()
                );
            }
        });
        e.initialize(); return e;
    }
}
