package com.sparta.one_stop.global.audit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Slf4j @Service @RequiredArgsConstructor
public class SuspiciousActivityDetector {
 private static final int LOGIN_FAILURE_THRESHOLD=10;
 private static final int AUTHZ_THRESHOLD=5;
 private final SecurityAuditLogRepository repository;private final SecurityAuditCryptoService crypto;private final SecurityAuditService audit;
 public boolean detectBruteForce(String clientIp){
  String hash=crypto.hmacSha256(clientIp);if(hash==null)return false;
  long count=repository.countByClientIpHashAndEventTypeAndOccurredAtAfter(hash,SecurityAuditEventType.LOGIN_FAILED,LocalDateTime.now().minusMinutes(5));
  if(count<LOGIN_FAILURE_THRESHOLD)return false;
  audit.record(SecurityAuditEvent.builder().eventType(SecurityAuditEventType.LOGIN_FAIL_BURST_DETECTED).result("DETECTED").clientIp(clientIp).ruleCode("AUTH_FAIL_BURST").suspicious(true).build());
  log.warn("[SECURITY_PATTERN] login failure burst count={}",count);return true;
 }
 public boolean detectAuthzAbuse(Long userId){
  if(userId==null)return false;
  long count=repository.countByActorUserIdAndEventTypeAndOccurredAtAfter(userId,SecurityAuditEventType.ACCESS_DENIED,LocalDateTime.now().minusMinutes(5));
  if(count<AUTHZ_THRESHOLD)return false;
  audit.record(SecurityAuditEvent.builder().eventType(SecurityAuditEventType.SUSPICIOUS_PATTERN_DETECTED).actorUserId(userId).result("DETECTED").ruleCode("AUTHZ_ABUSE").suspicious(true).build());return true;
 }
}
