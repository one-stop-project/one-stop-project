package com.sparta.one_stop.domain.admin.security;
import java.time.LocalDateTime;
public record SecurityActionResponse(Long targetUserId,SecurityActionType actionType,String reasonCode,LocalDateTime startedAt,LocalDateTime expiresAt){
 static SecurityActionResponse from(UserSecurityAction a){return new SecurityActionResponse(a.getTargetUserId(),a.getActionType(),a.getReasonCode(),a.getStartedAt(),a.getExpiresAt());}
}
