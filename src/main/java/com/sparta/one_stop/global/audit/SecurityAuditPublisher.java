package com.sparta.one_stop.global.audit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component @RequiredArgsConstructor
public class SecurityAuditPublisher {
    private final ApplicationEventPublisher publisher;
    private final SecurityAuditSanitizer sanitizer;
    public void publish(SecurityAuditEvent event) {
        PreparedSecurityAuditEvent prepared = sanitizer.sanitize(event);
        if (isSuccessfulMutationInsideTransaction(event)) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.publishEvent(prepared);
                }
            });
            return;
        }
        publisher.publishEvent(prepared);
    }

    private boolean isSuccessfulMutationInsideTransaction(SecurityAuditEvent event) {
        return "SUCCESS".equalsIgnoreCase(event.result())
            && TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive();
    }
}
