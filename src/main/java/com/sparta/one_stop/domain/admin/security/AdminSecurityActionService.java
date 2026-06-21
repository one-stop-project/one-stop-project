package com.sparta.one_stop.domain.admin.security;
import com.sparta.one_stop.domain.auth.event.AllDevicesLogoutEvent;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.audit.*;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Transactional
    public SecurityActionResponse execute(
        Long adminId,
        Long targetId,
        SecurityActionRequest request
    ) {
        if (request.actionType() == SecurityActionType.SUSPEND && adminId.equals(targetId)) {
            throw new CustomException(ErrorCode.SECURITY_004);
        }

        String sanitizedReasonDetail = sanitizer.sanitizeDetail(request.reasonDetail());
        User user = users.findByIdForUpdate(targetId)
            .orElseThrow(() -> new CustomException(ErrorCode.SECURITY_002));

        LocalDateTime expiresAt = applyAction(user, targetId, request);
        UserSecurityAction action = actions.save(UserSecurityAction.create(
            targetId,
            adminId,
            request.actionType(),
            request.reasonCode(),
            sanitizedReasonDetail,
            expiresAt
        ));

        recordAudit(adminId, targetId, request.actionType());
        return SecurityActionResponse.from(action);
    }

    private LocalDateTime applyAction(
        User user,
        Long targetId,
        SecurityActionRequest request
    ) {
        return switch (request.actionType()) {
            case SUSPEND -> {
                actions.findActiveSuspendAction(targetId)
                    .ifPresent(UserSecurityAction::deactivate);
                user.suspend();
                events.publishEvent(new AllDevicesLogoutEvent(targetId, "SUSPENDED"));
                int durationMinutes = request.durationMinutes() == null
                    ? DEFAULT_SUSPENSION_MINUTES
                    : request.durationMinutes();
                yield LocalDateTime.now().plusMinutes(durationMinutes);
            }
            case UNSUSPEND -> {
                user.reactivate();
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

    private void recordAudit(Long adminId, Long targetId, SecurityActionType actionType) {
        SecurityAuditEventType eventType = switch (actionType) {
            case SUSPEND -> SecurityAuditEventType.USER_SUSPENDED;
            case UNSUSPEND -> SecurityAuditEventType.USER_UNSUSPENDED;
            case FORCE_LOGOUT -> SecurityAuditEventType.USER_FORCE_LOGOUT;
        };

        audit.record(SecurityAuditEvent.builder()
            .eventType(eventType)
            .actorUserId(adminId)
            .targetUserId(targetId)
            .targetResource("User")
            .targetId(String.valueOf(targetId))
            .result("SUCCESS")
            .ruleCode("ADMIN_SECURITY_ACTION")
            .suspicious(actionType != SecurityActionType.UNSUSPEND)
            .build());
    }
}
