package com.sparta.one_stop.global.audit;

import java.time.LocalDateTime;

public record PreparedSecurityAuditEvent(
    SecurityAuditEventType eventType, SecuritySeverity severity,
    SecurityAuditEventType.Category category, Long actorUserId, String actorRole,
    Long targetUserId, String targetResource, String targetId, String result,
    String errorCode, String detailMessage, String clientIpEncrypted,
    String clientIpHash, String clientIpPrefix, String userAgentHash,
    String deviceIdHash, String requestId, String ruleCode, String requestPath,
    boolean suspicious, LocalDateTime occurredAt
) { }
