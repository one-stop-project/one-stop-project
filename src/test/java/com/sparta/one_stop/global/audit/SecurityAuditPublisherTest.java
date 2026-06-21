package com.sparta.one_stop.global.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityAuditPublisherTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void successful_event_is_published_only_after_commit() {
        ApplicationEventPublisher applicationEvents = mock(ApplicationEventPublisher.class);
        SecurityAuditSanitizer sanitizer = mock(SecurityAuditSanitizer.class);
        SecurityAuditPublisher publisher = new SecurityAuditPublisher(applicationEvents, sanitizer);
        SecurityAuditEvent source = SecurityAuditEvent.builder()
            .eventType(SecurityAuditEventType.USER_SUSPENDED)
            .result("SUCCESS")
            .build();
        PreparedSecurityAuditEvent prepared = prepared(SecurityAuditEventType.USER_SUSPENDED, "SUCCESS");
        when(sanitizer.sanitize(source)).thenReturn(prepared);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        publisher.publish(source);

        verify(applicationEvents, never()).publishEvent(prepared);
        TransactionSynchronizationManager.getSynchronizations()
            .forEach(synchronization -> synchronization.afterCommit());
        verify(applicationEvents).publishEvent(prepared);
    }

    @Test
    void blocked_event_is_published_even_when_transaction_rolls_back() {
        ApplicationEventPublisher applicationEvents = mock(ApplicationEventPublisher.class);
        SecurityAuditSanitizer sanitizer = mock(SecurityAuditSanitizer.class);
        SecurityAuditPublisher publisher = new SecurityAuditPublisher(applicationEvents, sanitizer);
        SecurityAuditEvent source = SecurityAuditEvent.builder()
            .eventType(SecurityAuditEventType.LOGIN_BLOCKED_SUSPENDED)
            .result("BLOCKED")
            .build();
        PreparedSecurityAuditEvent prepared = prepared(SecurityAuditEventType.LOGIN_BLOCKED_SUSPENDED, "BLOCKED");
        when(sanitizer.sanitize(source)).thenReturn(prepared);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        publisher.publish(source);

        verify(applicationEvents).publishEvent(prepared);
    }

    private PreparedSecurityAuditEvent prepared(SecurityAuditEventType type, String result) {
        return new PreparedSecurityAuditEvent(
            type,
            type.getSeverity(),
            type.getCategory(),
            null,
            null,
            null,
            null,
            null,
            result,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            LocalDateTime.now()
        );
    }
}
