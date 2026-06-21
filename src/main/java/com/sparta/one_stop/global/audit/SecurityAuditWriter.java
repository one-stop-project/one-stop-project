package com.sparta.one_stop.global.audit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component @RequiredArgsConstructor
public class SecurityAuditWriter {
    private final SecurityAuditLogRepository repository;
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void save(PreparedSecurityAuditEvent event){repository.save(SecurityAuditLog.from(event));}
}
