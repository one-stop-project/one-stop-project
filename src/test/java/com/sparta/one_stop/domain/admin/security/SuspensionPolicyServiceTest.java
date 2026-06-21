package com.sparta.one_stop.domain.admin.security;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.audit.SecurityAuditService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class SuspensionPolicyServiceTest {
 @Test void 만료된_정지는_접근시_자동해제된다(){
  var repo=mock(UserSecurityActionRepository.class);var audit=mock(SecurityAuditService.class);var user=mock(User.class);var action=mock(UserSecurityAction.class);
  given(user.isSuspended()).willReturn(true);given(user.getId()).willReturn(2L);given(repo.findActiveSuspendAction(2L)).willReturn(Optional.of(action));given(action.isExpired(any())).willReturn(true);
  new SuspensionPolicyService(repo,audit).validateOrRelease(user);
  verify(action).deactivate();verify(user).reactivate();verify(audit).record(any());
 }
}
