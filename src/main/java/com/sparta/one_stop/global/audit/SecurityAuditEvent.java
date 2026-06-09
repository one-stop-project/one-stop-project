package com.sparta.one_stop.global.audit;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SecurityAuditEvent(
        SecurityAuditEventType eventType,

        // Actor 정보 — SecurityContext에서 자동 캡처되지만 명시 가능
        Long actorUserId,
        String actorEmail,
        String actorRole,

        // Target 정보
        String targetResource,
        String targetId,

        // Result
        String result,
        String errorCode,
        String errorMessage,

        // Method 정보 — AOP에서 자동 채움
        String methodName,
        String methodArgs,

        // Metadata — JSON 형태로 자유롭게
        String metadata,

        // 명시적 시각 (보통 null — 자동 현재시각)
        LocalDateTime occurredAt
) {
}
