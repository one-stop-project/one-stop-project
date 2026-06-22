package com.sparta.one_stop.global.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SecurityEventListener {

    private static final Pattern SECURITY_TARGET_PATH = Pattern.compile(
        "^/api/admin/security/users/(\\d+)(?:/actions)?$");

    private final SecurityAuditService audit;

    @EventListener
    public void onDenied(AuthorizationDeniedEvent<?> ignored) {
        HttpServletRequest request = currentRequest();
        String path = request == null ? null : request.getRequestURI();
        Long targetUserId = targetUserId(path);
        boolean securityActionPath = path != null && SECURITY_TARGET_PATH.matcher(path).matches();

        audit.record(SecurityAuditEvent.builder()
            .eventType(SecurityAuditEventType.ACCESS_DENIED)
            .targetUserId(targetUserId)
            .targetResource(securityActionPath ? "User" : null)
            .targetId(targetUserId == null ? null : String.valueOf(targetUserId))
            .result("FAILURE")
            .errorCode(securityActionPath ? "SECURITY_003" : "AUTH_011")
            .ruleCode(securityActionPath
                ? "SECURITY_ACTION_ACCESS_DENIED"
                : "AUTHORIZATION_DENIED")
            .suspicious(securityActionPath)
            .build());
    }

    private Long targetUserId(String path) {
        if (path == null) return null;
        Matcher matcher = SECURITY_TARGET_PATH.matcher(path);
        if (!matcher.matches()) return null;
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
            ? servletAttributes.getRequest()
            : null;
    }
}
