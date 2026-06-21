package com.sparta.one_stop.global.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Locale;

@Component @RequiredArgsConstructor
public class SecurityAuditSanitizer {
    private final SecurityAuditCryptoService crypto;

    public PreparedSecurityAuditEvent sanitize(SecurityAuditEvent e) {
        return new PreparedSecurityAuditEvent(
            e.eventType(), e.eventType().getSeverity(), e.eventType().getCategory(),
            e.actorUserId(), e.actorRole(), e.targetUserId(), e.targetResource(), e.targetId(),
            e.result()==null?"SUCCESS":cut(e.result(),20), e.errorCode(), cleanDetail(e.errorMessage()),
            crypto.encryptIp(e.clientIp()), crypto.hmacSha256(e.clientIp()), crypto.toIpPrefix(e.clientIp()),
            crypto.hmacSha256(e.userAgent()), crypto.hmacSha256(e.deviceId()), e.requestId(), e.ruleCode(),
            cleanPath(e.requestPath()), Boolean.TRUE.equals(e.suspicious()) || e.eventType().isSuspiciousByDefault(),
            e.occurredAt()==null? LocalDateTime.now():e.occurredAt());
    }

    private String cleanPath(String value) {
        if (value==null) return null; int q=value.indexOf('?'); return cut(q<0?value:value.substring(0,q),200);
    }
    private String cleanDetail(String value) {
        if (value==null) return null; String v=value.toLowerCase(Locale.ROOT);
        if (v.contains("token")||v.contains("cookie")||v.contains("password")||v.contains("authorization")||value.contains("@"))
            return "[REDACTED_SECURITY_DETAIL]";
        return cut(value,1000);
    }
    private String cut(String value,int max){return value!=null&&value.length()>max?value.substring(0,max):value;}
}
