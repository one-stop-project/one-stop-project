package com.sparta.one_stop.global.audit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Slf4j @Component @RequiredArgsConstructor
public class SecurityAuditListener {
    private static final long FAILURE_LOG_INTERVAL_MILLIS = 60_000L;
    private final SecurityAuditWriter writer;
    private final MeterRegistry meterRegistry;
    private final AtomicLong nextFailureLogAt = new AtomicLong(0L);
    private final LongAdder failuresSinceLastLog = new LongAdder();

    @Async("securityAuditExecutor") @EventListener
    public void handle(PreparedSecurityAuditEvent event) {
        try { writer.save(event); }
        catch(Exception e) {
            Counter.builder("security.audit.save.failures")
                .tag("eventType", event.eventType().name())
                .register(meterRegistry)
                .increment();
            failuresSinceLastLog.increment();
            long now = System.currentTimeMillis();
            long next = nextFailureLogAt.get();
            if (now >= next && nextFailureLogAt.compareAndSet(next, now + FAILURE_LOG_INTERVAL_MILLIS)) {
                log.warn(
                    "[SECURITY_AUDIT_SAVE_FAILED] type={}, actor={}, target={}, failuresSinceLastLog={}, msg={}",
                    event.eventType(), event.actorUserId(), event.targetUserId(),
                    failuresSinceLastLog.sumThenReset(), e.getMessage()
                );
                log.debug("[SECURITY_AUDIT_SAVE_FAILED] sampled stack trace", e);
            }
        }
    }
}
