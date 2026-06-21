package com.sparta.one_stop.global.audit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component @RequiredArgsConstructor
public class DeviceAbuseDetector {
 private static final int THRESHOLD=5;
 private final SecurityAuditLogRepository repository;private final SecurityAuditService audit;
 public boolean detectMultiDeviceAbuse(Long userId){
  if(userId==null)return false;
  long count=repository.countByActorUserIdAndEventTypeAndOccurredAtAfter(userId,SecurityAuditEventType.NEW_DEVICE_REGISTERED,LocalDateTime.now().minusMinutes(5));
  if(count<THRESHOLD)return false;
  audit.record(SecurityAuditEvent.builder().eventType(SecurityAuditEventType.SUSPICIOUS_PATTERN_DETECTED).actorUserId(userId).result("DETECTED").ruleCode("NEW_DEVICE_SPIKE").suspicious(true).build());return true;
 }
}
