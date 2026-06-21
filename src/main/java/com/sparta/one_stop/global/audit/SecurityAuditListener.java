package com.sparta.one_stop.global.audit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j @Component @RequiredArgsConstructor
public class SecurityAuditListener {
    private final SecurityAuditWriter writer;
    @Async("securityAuditExecutor") @EventListener
    public void handle(PreparedSecurityAuditEvent event) {
        try { writer.save(event); }
        catch(Exception e) { log.warn("[SECURITY_AUDIT_SAVE_FAILED] type={}, actor={}, target={}",
            event.eventType(),event.actorUserId(),event.targetUserId(),e); }
    }
}
