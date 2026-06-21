package com.sparta.one_stop.global.audit;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class SecurityEventListener {
 private final SecurityAuditService audit;
 @EventListener public void onDenied(AuthorizationDeniedEvent<?> ignored){
  audit.record(SecurityAuditEvent.builder().eventType(SecurityAuditEventType.ACCESS_DENIED).result("BLOCKED").errorCode("AUTH_011").build());
 }
}
