package com.sparta.one_stop.global.audit;

import lombok.Builder;
import java.time.LocalDateTime;

@Builder(toBuilder = true)
public record SecurityAuditEvent(
    SecurityAuditEventType eventType,
    Long actorUserId, String actorEmail, String actorRole,
    Long targetUserId, String targetResource, String targetId,
    String result, String errorCode, String errorMessage,
    String methodName, String methodArgs, String metadata,
    String clientIp, String userAgent, String deviceId,
    String requestId, String ruleCode, String requestPath,
    Boolean suspicious, LocalDateTime occurredAt
) { }
