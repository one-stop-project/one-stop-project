package com.sparta.one_stop.global.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Spring Security 이벤트 자동 캡처
 *
 * 캡처 대상
 *
 *   AuthenticationSuccessEvent — 로그인 성공
 *   AuthenticationFailureBadCredentialsEvent — 비밀번호 불일치
 *   AuthenticationFailureLockedEvent — 계정 잠금
 *   AuthorizationDeniedEvent — 권한 거부 (Spring Security 6.1+)
 *
 *
 * 주의
 * Spring Security 이벤트는 {@code AuthenticationEventPublisher}가 자동 발행.
 * 별도 설정 없이 ProviderManager가 기본 발행함.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityEventListener {

    private final SecurityAuditService auditService;

    /**
     * 로그인 성공 자동 캡처
     */
    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        try {
            String email = event.getAuthentication().getName();
            auditService.record(SecurityAuditEvent.builder()
                    .eventType(SecurityAuditEventType.LOGIN_SUCCESS)
                    .actorEmail(email)
                    .result("SUCCESS")
                    .occurredAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[SECURITY_EVENT] 로그인 성공 기록 실패", e);
        }
    }

    /**
     * 비밀번호 불일치 자동 캡처
     */
    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        recordAuthFailure(event,
                SecurityAuditEventType.LOGIN_FAILED_BAD_CREDENTIALS,
                "AUTH_004",
                "이메일 또는 비밀번호가 일치하지 않습니다");
    }

    /**
     * 계정 잠금 상태 자동 캡처
     */
    @EventListener
    public void onAccountLocked(AuthenticationFailureLockedEvent event) {
        recordAuthFailure(event,
                SecurityAuditEventType.LOGIN_FAILED_ACCOUNT_LOCKED,
                "AUTH_005",
                "정지된 계정");
    }

    /**
     * 권한 거부 자동 캡처
     */
    @EventListener
    public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        try {
            auditService.record(SecurityAuditEvent.builder()
                    .eventType(SecurityAuditEventType.ACCESS_DENIED)
                    .result("FAILURE")
                    .errorCode("AUTH_011")
                    .errorMessage("접근 권한 없음")
                    .occurredAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[SECURITY_EVENT] 권한 거부 기록 실패", e);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  내부 헬퍼
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private void recordAuthFailure(AbstractAuthenticationFailureEvent event,
                                   SecurityAuditEventType type,
                                   String errorCode,
                                   String defaultMessage) {
        try {
            String email = event.getAuthentication().getName();
            String exMsg = event.getException() != null
                    ? event.getException().getMessage()
                    : defaultMessage;

            auditService.record(SecurityAuditEvent.builder()
                    .eventType(type)
                    .actorEmail(email)
                    .result("FAILURE")
                    .errorCode(errorCode)
                    .errorMessage(exMsg)
                    .occurredAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("[SECURITY_EVENT] 인증 실패 기록 실패", e);
        }
    }
}
