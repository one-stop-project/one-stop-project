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
import java.util.Locale;

@Service @RequiredArgsConstructor
public class AdminSecurityActionService {
 private final UserRepository users; private final UserSecurityActionRepository actions;
 private final ApplicationEventPublisher events; private final SecurityAuditService audit;
 @Transactional
 public SecurityActionResponse execute(Long adminId,Long targetId,SecurityActionRequest request){
  if(request.actionType()==SecurityActionType.SUSPEND&&adminId.equals(targetId))throw new CustomException(ErrorCode.SECURITY_004);
  validateReason(request.reasonDetail());
  User user=users.findByIdForUpdate(targetId).orElseThrow(()->new CustomException(ErrorCode.SECURITY_002));
  LocalDateTime expires=null;
  switch(request.actionType()){
   case SUSPEND->{user.suspend();expires=LocalDateTime.now().plusMinutes(request.durationMinutes()==null?1440:request.durationMinutes());events.publishEvent(new AllDevicesLogoutEvent(targetId,"SUSPENDED"));}
   case UNSUSPEND->{user.reactivate();actions.findActiveSuspendAction(targetId).ifPresent(UserSecurityAction::deactivate);}
   case FORCE_LOGOUT->{user.increaseTokenVersion();events.publishEvent(new AllDevicesLogoutEvent(targetId,"SECURITY_BREACH"));}
  }
  UserSecurityAction action=actions.save(UserSecurityAction.create(targetId,adminId,request.actionType(),request.reasonCode(),request.reasonDetail(),expires));
  SecurityAuditEventType type=switch(request.actionType()){case SUSPEND->SecurityAuditEventType.USER_SUSPENDED;case UNSUSPEND->SecurityAuditEventType.USER_UNSUSPENDED;case FORCE_LOGOUT->SecurityAuditEventType.USER_FORCE_LOGOUT;};
  audit.record(SecurityAuditEvent.builder().eventType(type).actorUserId(adminId).targetUserId(targetId).targetResource("User").targetId(String.valueOf(targetId)).result("SUCCESS").ruleCode("ADMIN_SECURITY_ACTION").suspicious(request.actionType()!=SecurityActionType.UNSUSPEND).build());
  return SecurityActionResponse.from(action);
 }
 private void validateReason(String detail){if(detail==null)return;String v=detail.toLowerCase(Locale.ROOT);if(v.contains("token")||v.contains("password")||v.contains("cookie")||detail.contains("@"))throw new CustomException(ErrorCode.SECURITY_005);}
}
