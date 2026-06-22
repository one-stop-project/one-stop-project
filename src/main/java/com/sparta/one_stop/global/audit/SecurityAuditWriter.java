package com.sparta.one_stop.global.audit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component @RequiredArgsConstructor
public class SecurityAuditWriter {
    private final SecurityAuditLogRepository repository;
    // 비동기 리스너에는 요청 트랜잭션이 전파되지 않는다. 동기 fallback에서도
    // 감사 저장 실패가 비즈니스 트랜잭션을 오염시키지 않도록 독립 경계를 유지한다.
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void save(PreparedSecurityAuditEvent event){repository.save(SecurityAuditLog.from(event));}
}
