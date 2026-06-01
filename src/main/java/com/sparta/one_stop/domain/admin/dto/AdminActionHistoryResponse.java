package com.sparta.one_stop.domain.admin.dto;

import com.sparta.one_stop.domain.admin.entity.AdminActionHistory;
import com.sparta.one_stop.global.enums.admin.AdminActionTarget;
import com.sparta.one_stop.global.enums.admin.AdminActionType;

import java.time.LocalDateTime;

public record AdminActionHistoryResponse(
    Long id,
    Long actorId,
    AdminActionTarget targetType,
    Long targetId,
    AdminActionType action,
    String reason,
    LocalDateTime createdAt
) {
    public static AdminActionHistoryResponse from(AdminActionHistory history) {
        return new AdminActionHistoryResponse(
            history.getId(),
            history.getActorId(),
            history.getTargetType(),
            history.getTargetId(),
            history.getAction(),
            history.getReason(),
            history.getCreatedAt()
        );
    }
}
