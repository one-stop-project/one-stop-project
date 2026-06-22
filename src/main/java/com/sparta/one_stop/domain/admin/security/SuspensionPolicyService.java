package com.sparta.one_stop.domain.admin.security;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.event.UserStatusChangedEvent;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.audit.SecurityAuditEvent;
import com.sparta.one_stop.global.audit.SecurityAuditEventType;
import com.sparta.one_stop.global.audit.SecurityAuditService;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class SuspensionPolicyService {

    private final UserSecurityActionRepository actions;
    private final SecurityAuditService audit;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * 전달된 User는 빠른 ACTIVE 판정에만 사용한다. 정지 상태라면 관리자 조치와 동일한
     * User 행을 PESSIMISTIC_WRITE로 다시 조회하여 재정지와 자동 해제의 순서를 직렬화한다.
     */
    @Transactional
    public void validateOrRelease(User user) {
        if (!user.isSuspended()) return;

        User lockedUser = users.findByIdForUpdate(user.getId())
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));
        if (!lockedUser.isSuspended()) return;

        var activeSuspension = actions.findActiveSuspendAction(lockedUser.getId());
        if (activeSuspension.isPresent()
            && activeSuspension.get().isExpired(LocalDateTime.now(clock))) {
            activeSuspension.get().deactivate();
            lockedUser.reactivate();
            events.publishEvent(new UserStatusChangedEvent(lockedUser.getId()));
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
