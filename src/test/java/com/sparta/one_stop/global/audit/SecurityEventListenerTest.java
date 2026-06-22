package com.sparta.one_stop.global.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SecurityEventListenerTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void deniedSecurityActionIncludesTargetAndSecurityRuleCode() {
        SecurityAuditService audit = mock(SecurityAuditService.class);
        SecurityEventListener listener = new SecurityEventListener(audit);
        MockHttpServletRequest request = new MockHttpServletRequest(
            "POST", "/api/admin/security/users/42/actions");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        listener.onDenied(mock(AuthorizationDeniedEvent.class));

        var captor = org.mockito.ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(SecurityAuditEventType.ACCESS_DENIED);
        assertThat(captor.getValue().targetUserId()).isEqualTo(42L);
        assertThat(captor.getValue().result()).isEqualTo("FAILURE");
        assertThat(captor.getValue().errorCode()).isEqualTo("SECURITY_003");
        assertThat(captor.getValue().ruleCode()).isEqualTo("SECURITY_ACTION_ACCESS_DENIED");
    }
}
