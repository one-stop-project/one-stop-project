package com.sparta.one_stop.domain.admin.security;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.audit.*;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class SuspensionPolicyService {
 private final UserSecurityActionRepository actions; private final SecurityAuditService audit;
 @Transactional
 public void validateOrRelease(User user){
  if(!user.isSuspended())return;
  var active=actions.findActiveSuspendAction(user.getId());
  if(active.isPresent()&&active.get().isExpired(LocalDateTime.now())){
   active.get().deactivate();user.reactivate();audit.record(SecurityAuditEvent.builder().eventType(SecurityAuditEventType.USER_SUSPENSION_EXPIRED_AUTO_RELEASED).actorUserId(user.getId()).targetUserId(user.getId()).result("SUCCESS").ruleCode("SUSPENSION_EXPIRED").build());return;
  }
  audit.record(SecurityAuditEvent.builder().eventType(SecurityAuditEventType.LOGIN_BLOCKED_SUSPENDED).actorUserId(user.getId()).targetUserId(user.getId()).result("BLOCKED").ruleCode("SUSPENDED_USER_LOGIN").suspicious(true).build());
  throw new CustomException(ErrorCode.AUTH_005);
 }
}
