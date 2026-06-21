package com.sparta.one_stop.global.audit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class SecurityAuditPublisher {
    private final ApplicationEventPublisher publisher;
    private final SecurityAuditSanitizer sanitizer;
    public void publish(SecurityAuditEvent event) { publisher.publishEvent(sanitizer.sanitize(event)); }
}
