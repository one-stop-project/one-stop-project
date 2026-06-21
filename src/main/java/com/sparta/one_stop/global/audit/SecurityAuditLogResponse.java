package com.sparta.one_stop.global.audit;
import java.time.LocalDateTime;
public record SecurityAuditLogResponse(Long id,Long actorUserId,Long targetUserId,SecurityAuditEventType eventType,
 SecuritySeverity severity,String clientIpPrefix,String requestId,String ruleCode,String requestPath,String result,
 String errorCode,boolean suspicious,LocalDateTime occurredAt){
 public static SecurityAuditLogResponse from(SecurityAuditLog l){return new SecurityAuditLogResponse(l.getId(),l.getActorUserId(),l.getTargetUserId(),l.getEventType(),l.getSeverity(),l.getClientIpPrefix(),l.getRequestId(),l.getRuleCode(),l.getRequestPath(),l.getResult(),l.getErrorCode(),l.isSuspicious(),l.getOccurredAt());}
}
