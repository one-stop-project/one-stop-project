package com.sparta.one_stop.domain.admin.security;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.audit.SecurityAuditService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SuspensionPolicyServiceTest {

    @Test
    void 만료된_정지는_접근시_자동해제된다() {
        var actions = mock(UserSecurityActionRepository.class);
        var audit = mock(SecurityAuditService.class);
        var users = mock(UserRepository.class);
        var user = mock(User.class);
        var action = mock(UserSecurityAction.class);

        given(user.isSuspended()).willReturn(true);
        given(user.getId()).willReturn(2L);
        given(users.findByIdForUpdate(2L)).willReturn(Optional.of(user));
        given(actions.findActiveSuspendAction(2L)).willReturn(Optional.of(action));
        given(action.isExpired(any())).willReturn(true);

        new SuspensionPolicyService(actions, audit, users).validateOrRelease(user);

        verify(action).deactivate();
        verify(user).reactivate();
        verify(audit).record(any());
    }
}
