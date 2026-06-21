package com.sparta.one_stop.global.audit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditSanitizerTest {
 @Test void 원문_PII를_정제하고_쿼리스트링을_제거한다(){
  var crypto=new SecurityAuditCryptoService("hmac-secret-for-test-over-32-bytes","aes-secret-for-test-over-32-bytes","v1");
  var sanitizer=new SecurityAuditSanitizer(crypto);
  var result=sanitizer.sanitize(SecurityAuditEvent.builder().eventType(SecurityAuditEventType.REFRESH_TOKEN_REUSE_DETECTED)
   .actorEmail("user@example.com").clientIp("10.20.30.40").userAgent("browser raw").deviceId("device raw")
   .requestPath("/api/auth/refresh?token=secret").errorMessage("token=user@example.com").result("BLOCKED").build());
  assertThat(result.clientIpEncrypted()).doesNotContain("10.20.30.40");
  assertThat(result.userAgentHash()).doesNotContain("browser raw");
  assertThat(result.deviceIdHash()).doesNotContain("device raw");
  assertThat(result.requestPath()).isEqualTo("/api/auth/refresh");
  assertThat(result.detailMessage()).isEqualTo("token=[REDACTED]");
  assertThat(result.suspicious()).isTrue();
 }

 @Test void masks_only_sensitive_values_and_limits_database_fields(){
  var crypto=new SecurityAuditCryptoService("hmac-secret-for-test-over-32-bytes","aes-secret-for-test-over-32-bytes","v1");
  var sanitizer=new SecurityAuditSanitizer(crypto);
  var result=sanitizer.sanitize(SecurityAuditEvent.builder()
   .eventType(SecurityAuditEventType.LOGIN_FAILED)
   .actorRole("A".repeat(30)).targetResource("R".repeat(40)).targetId("I".repeat(60))
   .errorCode("E".repeat(120)).requestId("Q".repeat(120)).ruleCode("C".repeat(120))
   .errorMessage("token 탈취 의심, token=secret-value, 연락처 user@example.com")
   .build());
  assertThat(result.actorRole()).hasSize(20);
  assertThat(result.targetResource()).hasSize(30);
  assertThat(result.targetId()).hasSize(50);
  assertThat(result.errorCode()).hasSize(100);
  assertThat(result.requestId()).hasSize(100);
  assertThat(result.ruleCode()).hasSize(100);
  assertThat(result.detailMessage())
   .contains("token 탈취 의심", "token=[REDACTED]", "[REDACTED_EMAIL]")
   .doesNotContain("secret-value", "user@example.com");
 }
}
