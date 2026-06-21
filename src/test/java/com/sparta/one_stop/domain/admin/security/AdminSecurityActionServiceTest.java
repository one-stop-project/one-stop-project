package com.sparta.one_stop.domain.admin.security;
import com.sparta.one_stop.domain.auth.event.AllDevicesLogoutEvent;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.audit.SecurityAuditService;
import com.sparta.one_stop.global.audit.SecurityAuditSanitizer;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSecurityActionServiceTest {
 @Mock UserRepository users; @Mock UserSecurityActionRepository actions; @Mock ApplicationEventPublisher events; @Mock SecurityAuditService audit;
 @Mock SecurityAuditSanitizer sanitizer;
 @InjectMocks AdminSecurityActionService service;
 @Test void 관리자는_자기자신을_정지할수없다(){
  var request=new SecurityActionRequest(SecurityActionType.SUSPEND,"TEST",null,60);
  assertThatThrownBy(()->service.execute(1L,1L,request)).isInstanceOf(CustomException.class)
   .satisfies(e->assertThat(((CustomException)e).getErrorCode()).isEqualTo(ErrorCode.SECURITY_004));
  verifyNoInteractions(users);
 }
 @Test void 정지는_상태변경_이력저장_전체로그아웃을_수행한다(){
  User user=mock(User.class);given(users.findByIdForUpdate(2L)).willReturn(Optional.of(user));
  given(actions.save(any())).willAnswer(i->i.getArgument(0));
  var response=service.execute(1L,2L,new SecurityActionRequest(SecurityActionType.SUSPEND,"ABUSE",null,60));
 verify(user).suspend();verify(actions).save(any(UserSecurityAction.class));verify(events).publishEvent(any(AllDevicesLogoutEvent.class));
 assertThat(response.actionType()).isEqualTo(SecurityActionType.SUSPEND);assertThat(response.expiresAt()).isNotNull();
 }

 @Test void 재정지는_기존이력을_비활성화하고_민감값을_마스킹한다(){
  User user=mock(User.class);UserSecurityAction active=mock(UserSecurityAction.class);
  given(users.findByIdForUpdate(2L)).willReturn(Optional.of(user));
  given(actions.findActiveSuspendAction(2L)).willReturn(Optional.of(active));
  given(sanitizer.sanitizeDetail("token=secret")).willReturn("token=[REDACTED]");
  given(actions.save(any())).willAnswer(i->i.getArgument(0));

  service.execute(1L,2L,new SecurityActionRequest(SecurityActionType.SUSPEND,"ABUSE","token=secret",60));

  verify(active).deactivate();
  ArgumentCaptor<UserSecurityAction> saved=ArgumentCaptor.forClass(UserSecurityAction.class);
  verify(actions).save(saved.capture());
  assertThat(saved.getValue().getReasonDetail()).isEqualTo("token=[REDACTED]");
 }
}
