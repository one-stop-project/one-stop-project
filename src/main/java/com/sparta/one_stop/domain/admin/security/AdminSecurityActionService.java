package com.sparta.one_stop.domain.admin.security;

import com.sparta.one_stop.domain.auth.event.AllDevicesLogoutEvent;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.event.UserStatusChangedEvent;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.audit.SecurityAuditEvent;
import com.sparta.one_stop.global.audit.SecurityAuditEventType;
import com.sparta.one_stop.global.audit.SecurityAuditSanitizer;
import com.sparta.one_stop.global.audit.SecurityAuditService;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.enums.user.UserStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminSecurityActionService {

    private static final int DEFAULT_SUSPENSION_MINUTES = 1_440;

    private final UserRepository users;
    private final UserSecurityActionRepository actions;
    private final ApplicationEventPublisher events;
    private final SecurityAuditService audit;
    private final SecurityAuditSanitizer sanitizer;
    private final Clock clock;

    @Transactional
    public SecurityActionResponse execute(
        Long adminId,
        Long targetId,
        SecurityActionRequest request
    ) {
        validateActorPermission(adminId, targetId, request.actionType());

        if (adminId.equals(targetId)) {
            throw rejection(adminId, targetId, request.actionType(), ErrorCode.SECURITY_004,
                "SECURITY_ACTION_SELF_TARGET");
        }

        User user = users.findByIdForUpdate(targetId)
            .orElseThrow(() -> rejection(adminId, targetId, request.actionType(),
                ErrorCode.SECURITY_002, "SECURITY_ACTION_TARGET_NOT_FOUND"));

        validateSanctionableRole(adminId, user, request.actionType());
        validateStateTransition(adminId, user, request.actionType());

        String sanitizedReasonDetail = sanitizer.sanitizeDetail(request.reasonDetail());
        LocalDateTime expiresAt = applyAction(user, request);
        UserSecurityAction action = actions.save(UserSecurityAction.create(
            targetId,
            adminId,
            request.actionType(),
            request.reasonCode(),
            sanitizedReasonDetail,
            expiresAt
        ));

        recordSuccess(adminId, targetId, request.actionType());
        return SecurityActionResponse.from(action);
    }

    private void validateActorPermission(
        Long adminId,
        Long targetId,
        SecurityActionType actionType
    ) {
        User actor = users.findById(adminId).orElse(null);
        if (actor == null || actor.getRole() != UserRole.SUPER_ADMIN) {
            throw rejection(adminId, targetId, actionType, ErrorCode.SECURITY_003,
                "SECURITY_ACTION_ACTOR_NOT_ALLOWED");
        }
    }

    @Transactional(readOnly = true)
    public SecurityTargetResponse getTarget(Long adminId, Long userId) {
        User user = users.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.SECURITY_002));
        audit.record(SecurityAuditEvent.builder()
            .eventType(SecurityAuditEventType.SECURITY_TARGET_VIEWED)
            .actorUserId(adminId)
            .targetUserId(userId)
            .targetResource("User")
            .targetId(String.valueOf(userId))
            .result("SUCCESS")
            .ruleCode("SECURITY_TARGET_VIEW")
            .occurredAt(LocalDateTime.now(clock))
            .build());
        return SecurityTargetResponse.from(user);
    }

    private void validateSanctionableRole(
        Long adminId,
        User user,
        SecurityActionType actionType
    ) {
        if (user.getRole() != UserRole.BUYER && user.getRole() != UserRole.SELLER) {
            throw rejection(adminId, user.getId(), actionType, ErrorCode.SECURITY_006,
                "SECURITY_ACTION_TARGET_ROLE_NOT_ALLOWED");
        }
    }

    private void validateStateTransition(
        Long adminId,
        User user,
        SecurityActionType actionType
    ) {
        UserStatus status = user.getStatus();
        boolean valid = switch (actionType) {
            case SUSPEND -> status == UserStatus.ACTIVE;
            case UNSUSPEND -> status == UserStatus.SUSPENDED;
            case FORCE_LOGOUT -> status != UserStatus.WITHDRAWN;
        };
        if (!valid) {
            throw rejection(adminId, user.getId(), actionType, ErrorCode.SECURITY_007,
                "SECURITY_ACTION_INVALID_STATE");
        }
    }

    private LocalDateTime applyAction(User user, SecurityActionRequest request) {
        Long targetId = user.getId();
        return switch (request.actionType()) {
            case SUSPEND -> {
                actions.findActiveSuspendAction(targetId)
                    .ifPresent(UserSecurityAction::deactivate);
                user.suspend();
                events.publishEvent(new UserStatusChangedEvent(targetId));
                events.publishEvent(new AllDevicesLogoutEvent(targetId, "SUSPENDED"));
                int durationMinutes = request.durationMinutes() == null
                    ? DEFAULT_SUSPENSION_MINUTES
                    : request.durationMinutes();
                yield LocalDateTime.now(clock).plusMinutes(durationMinutes);
            }
            case UNSUSPEND -> {
                user.reactivate();
                events.publishEvent(new UserStatusChangedEvent(targetId));
                actions.findActiveSuspendAction(targetId)
                    .ifPresent(UserSecurityAction::deactivate);
                yield null;
            }
            case FORCE_LOGOUT -> {
                user.increaseTokenVersion();
                events.publishEvent(new AllDevicesLogoutEvent(targetId, "SECURITY_BREACH"));
                yield null;
            }
        };
    }

    private CustomException rejection(
        Long adminId,
        Long targetId,
        SecurityActionType actionType,
        ErrorCode errorCode,
        String ruleCode
    ) {
        audit.record(baseAuditEvent(adminId, targetId, actionType)
            .result("FAILURE")
            .errorCode(errorCode.getCode())
            .ruleCode(ruleCode)
            .suspicious(true)
            .occurredAt(LocalDateTime.now(clock))
            .build());
        return new CustomException(errorCode);
    }

    private void recordSuccess(Long adminId, Long targetId, SecurityActionType actionType) {
        audit.record(baseAuditEvent(adminId, targetId, actionType)
            .result("SUCCESS")
            .ruleCode("ADMIN_SECURITY_ACTION")
            .suspicious(actionType != SecurityActionType.UNSUSPEND)
            .occurredAt(LocalDateTime.now(clock))
            .build());
    }

    private SecurityAuditEvent.SecurityAuditEventBuilder baseAuditEvent(
        Long adminId,
        Long targetId,
        SecurityActionType actionType
    ) {
        return SecurityAuditEvent.builder()
            .eventType(eventTypeFor(actionType))
            .actorUserId(adminId)
            .targetUserId(targetId)
            .targetResource("User")
            .targetId(String.valueOf(targetId));
    }

    private SecurityAuditEventType eventTypeFor(SecurityActionType actionType) {
        return switch (actionType) {
            case SUSPEND -> SecurityAuditEventType.USER_SUSPENDED;
            case UNSUSPEND -> SecurityAuditEventType.USER_UNSUSPENDED;
            case FORCE_LOGOUT -> SecurityAuditEventType.USER_FORCE_LOGOUT;
        };
    }
}
