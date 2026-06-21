package com.sparta.one_stop.global.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Component @RequiredArgsConstructor
public class SecurityAuditSanitizer {
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern EMAIL = Pattern.compile(
        "(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"
    );
    private static final Pattern JWT = Pattern.compile(
        "\\beyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\b"
    );
    private static final Pattern BEARER = Pattern.compile(
        "(?i)Bearer\\s+[a-z0-9._~+/-]+=*"
    );
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
        "(?i)(authorization|password|cookie|token|secret|api[-_]?key)\\s*[:=]\\s*([^\\s,;]+)"
    );

    private final SecurityAuditCryptoService crypto;

    public PreparedSecurityAuditEvent sanitize(SecurityAuditEvent e) {
        return new PreparedSecurityAuditEvent(
            e.eventType(), e.eventType().getSeverity(), e.eventType().getCategory(),
            e.actorUserId(), cut(e.actorRole(),20), e.targetUserId(), cut(e.targetResource(),30), cut(e.targetId(),50),
            e.result()==null?"SUCCESS":cut(e.result(),20), cut(e.errorCode(),100), sanitizeDetail(e.errorMessage()),
            crypto.encryptIp(e.clientIp()), crypto.hmacSha256(e.clientIp()), crypto.toIpPrefix(e.clientIp()),
            crypto.hmacSha256(e.userAgent()), crypto.hmacSha256(e.deviceId()), cut(e.requestId(),100), cut(e.ruleCode(),100),
            cleanPath(e.requestPath()), Boolean.TRUE.equals(e.suspicious()) || e.eventType().isSuspiciousByDefault(),
            e.occurredAt()==null? LocalDateTime.now():e.occurredAt());
    }

    private String cleanPath(String value) {
        if (value==null) return null; int q=value.indexOf('?'); return cut(q<0?value:value.substring(0,q),200);
    }
    public String sanitizeDetail(String value) {
        if (value == null) return null;
        String masked = EMAIL.matcher(value).replaceAll("[REDACTED_EMAIL]");
        masked = JWT.matcher(masked).replaceAll("[REDACTED_JWT]");
        masked = BEARER.matcher(masked).replaceAll("Bearer " + REDACTED);
        masked = SECRET_ASSIGNMENT.matcher(masked).replaceAll("$1=" + REDACTED);
        return cut(masked,1000);
    }
    private String cut(String value,int max){return value!=null&&value.length()>max?value.substring(0,max):value;}
}
