package com.sparta.one_stop.domain.admin.security;

import com.sparta.one_stop.domain.auth.event.AllDevicesLogoutEvent;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.event.UserStatusChangedEvent;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.audit.SecurityAuditEvent;
import com.sparta.one_stop.global.audit.SecurityAuditSanitizer;
import com.sparta.one_stop.global.audit.SecurityAuditService;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.enums.user.UserStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSecurityActionServiceTest {

    private static final Long ACTOR_ID = 1L;
    private static final Long TARGET_ID = 2L;
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-06-22T00:00:00Z"), ZoneOffset.UTC);

    @Mock UserRepository users;
    @Mock UserSecurityActionRepository actions;
    @Mock ApplicationEventPublisher events;
    @Mock SecurityAuditService audit;
    @Mock SecurityAuditSanitizer sanitizer;

    AdminSecurityActionService service;

    @BeforeEach
    void setUp() {
        service = new AdminSecurityActionService(
            users, actions, events, audit, sanitizer, CLOCK);
        given(actions.save(any(UserSecurityAction.class)))
            .willAnswer(invocation -> invocation.getArgument(0));
        given(users.findById(ACTOR_ID))
            .willReturn(Optional.of(user(ACTOR_ID, UserRole.SUPER_ADMIN, UserStatus.ACTIVE)));
    }

    @Test
    void buyerAccountCanBeSuspended() {
        User buyer = user(TARGET_ID, UserRole.BUYER, UserStatus.ACTIVE);
        given(users.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(buyer));
        given(sanitizer.sanitizeDetail("policy violation")).willReturn("policy violation");

        SecurityActionResponse response = service.execute(ACTOR_ID, TARGET_ID,
            request(SecurityActionType.SUSPEND));

        assertThat(buyer.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(response.expiresAt()).isNotNull();
        verify(events).publishEvent(any(UserStatusChangedEvent.class));
        verify(events).publishEvent(any(AllDevicesLogoutEvent.class));
        verifySuccessAudit(SecurityActionType.SUSPEND);
    }

    @Test
    void sellerAccountCanBeForceLoggedOut() {
        User seller = user(TARGET_ID, UserRole.SELLER, UserStatus.ACTIVE);
        given(users.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(seller));

        service.execute(ACTOR_ID, TARGET_ID, request(SecurityActionType.FORCE_LOGOUT));

        assertThat(seller.getTokenVersion()).isEqualTo(1);
        verify(events).publishEvent(any(AllDevicesLogoutEvent.class));
        verifySuccessAudit(SecurityActionType.FORCE_LOGOUT);
    }

    @Test
    void adminAccountRejectsEverySecurityAction() {
        assertEveryActionRejectedForRole(UserRole.ADMIN);
    }

    @Test
    void superAdminAccountRejectsEverySecurityAction() {
        assertEveryActionRejectedForRole(UserRole.SUPER_ADMIN);
    }

    @Test
    void regularAdminActorCannotExecuteSecurityAction() {
        given(users.findById(ACTOR_ID))
            .willReturn(Optional.of(user(ACTOR_ID, UserRole.ADMIN, UserStatus.ACTIVE)));

        assertError(SecurityActionType.SUSPEND, ErrorCode.SECURITY_003);

        verify(users, never()).findByIdForUpdate(TARGET_ID);
        verifyFailureAudit(ErrorCode.SECURITY_003, "SECURITY_ACTION_ACTOR_NOT_ALLOWED");
    }

    @Test
    void withdrawnAccountRejectsEverySecurityAction() {
        for (SecurityActionType actionType : SecurityActionType.values()) {
            User withdrawn = user(TARGET_ID, UserRole.BUYER, UserStatus.WITHDRAWN);
            given(users.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(withdrawn));

            assertError(actionType, ErrorCode.SECURITY_007);
        }
        verify(audit, atLeastOnce()).record(any(SecurityAuditEvent.class));
    }

    @Test
    void suspendedAccountCanBeUnsuspended() {
        User suspended = user(TARGET_ID, UserRole.BUYER, UserStatus.SUSPENDED);
        UserSecurityAction active = org.mockito.Mockito.mock(UserSecurityAction.class);
        given(users.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(suspended));
        given(actions.findActiveSuspendAction(TARGET_ID)).willReturn(Optional.of(active));

        service.execute(ACTOR_ID, TARGET_ID, request(SecurityActionType.UNSUSPEND));

        assertThat(suspended.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(events).publishEvent(any(UserStatusChangedEvent.class));
        verify(active).deactivate();
        verifySuccessAudit(SecurityActionType.UNSUSPEND);
    }

    @Test
    void activeAccountCannotBeUnsuspended() {
        User active = user(TARGET_ID, UserRole.BUYER, UserStatus.ACTIVE);
        given(users.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(active));

        assertError(SecurityActionType.UNSUSPEND, ErrorCode.SECURITY_007);
        verifyFailureAudit(ErrorCode.SECURITY_007, "SECURITY_ACTION_INVALID_STATE");
    }

    @Test
    void selfSanctionIsRejectedForEveryAction() {
        for (SecurityActionType actionType : SecurityActionType.values()) {
            assertThatThrownBy(() -> service.execute(
                ACTOR_ID, ACTOR_ID, request(actionType)))
                .isInstanceOfSatisfying(CustomException.class,
                    exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SECURITY_004));
        }
        verify(users, never()).findByIdForUpdate(any());
        verify(audit, atLeastOnce()).record(any(SecurityAuditEvent.class));
    }

    @Test
    void missingTargetIsRejectedAndAudited() {
        given(users.findByIdForUpdate(TARGET_ID)).willReturn(Optional.empty());

        assertError(SecurityActionType.SUSPEND, ErrorCode.SECURITY_002);
        verifyFailureAudit(ErrorCode.SECURITY_002, "SECURITY_ACTION_TARGET_NOT_FOUND");
    }

    @Test
    void targetLookupReturnsServerCalculatedSanctionableFlag() {
        User admin = user(TARGET_ID, UserRole.ADMIN, UserStatus.ACTIVE);
        given(users.findById(TARGET_ID)).willReturn(Optional.of(admin));

        SecurityTargetResponse response = service.getTarget(ACTOR_ID, TARGET_ID);

        assertThat(response.role()).isEqualTo(UserRole.ADMIN);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.sanctionable()).isFalse();
        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().eventType())
            .isEqualTo(com.sparta.one_stop.global.audit.SecurityAuditEventType.SECURITY_TARGET_VIEWED);
    }

    @Test
    void activeBuyerIsReturnedAsSanctionableTarget() {
        User buyer = user(TARGET_ID, UserRole.BUYER, UserStatus.ACTIVE);
        given(users.findById(TARGET_ID)).willReturn(Optional.of(buyer));

        SecurityTargetResponse response = service.getTarget(ACTOR_ID, TARGET_ID);

        assertThat(response.id()).isEqualTo(TARGET_ID);
        assertThat(response.email()).isEqualTo("buyer2@test.com");
        assertThat(response.role()).isEqualTo(UserRole.BUYER);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.sanctionable()).isTrue();
    }

    @Test
    void withdrawnBuyerIsReturnedAsNonSanctionableTarget() {
        User withdrawn = user(TARGET_ID, UserRole.BUYER, UserStatus.WITHDRAWN);
        given(users.findById(TARGET_ID)).willReturn(Optional.of(withdrawn));

        assertThat(service.getTarget(ACTOR_ID, TARGET_ID).sanctionable()).isFalse();
    }

    private void assertEveryActionRejectedForRole(UserRole role) {
        for (SecurityActionType actionType : SecurityActionType.values()) {
            User target = user(TARGET_ID, role, UserStatus.ACTIVE);
            given(users.findByIdForUpdate(TARGET_ID)).willReturn(Optional.of(target));
            assertError(actionType, ErrorCode.SECURITY_006);
        }
        verify(audit, atLeastOnce()).record(any(SecurityAuditEvent.class));
    }

    private void assertError(SecurityActionType actionType, ErrorCode errorCode) {
        assertThatThrownBy(() -> service.execute(ACTOR_ID, TARGET_ID, request(actionType)))
            .isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private void verifySuccessAudit(SecurityActionType actionType) {
        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(captor.getValue().targetUserId()).isEqualTo(TARGET_ID);
        assertThat(captor.getValue().result()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().eventType().name()).contains(actionType.name().replace("UNSUSPEND", "UNSUSPENDED"));
    }

    private void verifyFailureAudit(ErrorCode errorCode, String ruleCode) {
        ArgumentCaptor<SecurityAuditEvent> captor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(captor.getValue().targetUserId()).isEqualTo(TARGET_ID);
        assertThat(captor.getValue().result()).isEqualTo("FAILURE");
        assertThat(captor.getValue().errorCode()).isEqualTo(errorCode.getCode());
        assertThat(captor.getValue().ruleCode()).isEqualTo(ruleCode);
        assertThat(captor.getValue().occurredAt()).isNotNull();
    }

    private SecurityActionRequest request(SecurityActionType actionType) {
        return new SecurityActionRequest(actionType, "POLICY", "policy violation", 60);
    }

    private User user(Long id, UserRole role, UserStatus status) {
        User user = User.builder()
            .email(role.name().toLowerCase() + id + "@test.com")
            .password("test-password")
            .name("target")
            .role(role)
            .build();
        ReflectionTestUtils.setField(user, "id", id);
        if (status == UserStatus.SUSPENDED) user.suspend();
        if (status == UserStatus.WITHDRAWN) user.withdraw();
        return user;
    }
}
