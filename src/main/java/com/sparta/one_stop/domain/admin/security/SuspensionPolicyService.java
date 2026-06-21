package com.sparta.one_stop.domain.admin.security;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.audit.SecurityAuditEvent;
import com.sparta.one_stop.global.audit.SecurityAuditEventType;
import com.sparta.one_stop.global.audit.SecurityAuditService;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SuspensionPolicyService {

    private final UserSecurityActionRepository actions;
    private final SecurityAuditService audit;
    private final UserRepository users;

    @Transactional
    public void validateOrRelease(User user) {
        if (!user.isSuspended()) return;

        User lockedUser = users.findByIdForUpdate(user.getId())
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));
        if (!lockedUser.isSuspended()) return;

        var activeSuspension = actions.findActiveSuspendAction(lockedUser.getId());
        if (activeSuspension.isPresent()
            && activeSuspension.get().isExpired(LocalDateTime.now())) {
            activeSuspension.get().deactivate();
            lockedUser.reactivate();
            audit.record(SecurityAuditEvent.builder()
                .eventType(SecurityAuditEventType.USER_SUSPENSION_EXPIRED_AUTO_RELEASED)
                .actorUserId(lockedUser.getId())
                .targetUserId(lockedUser.getId())
                .result("SUCCESS")
                .ruleCode("SUSPENSION_EXPIRED")
                .build());
            return;
        }

        audit.record(SecurityAuditEvent.builder()
            .eventType(SecurityAuditEventType.LOGIN_BLOCKED_SUSPENDED)
            .actorUserId(lockedUser.getId())
            .targetUserId(lockedUser.getId())
            .result("BLOCKED")
            .ruleCode("SUSPENDED_USER_LOGIN")
            .suspicious(true)
            .build());
        throw new CustomException(ErrorCode.AUTH_005);
    }
}
