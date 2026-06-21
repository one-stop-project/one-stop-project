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
  assertThat(result.detailMessage()).isEqualTo("[REDACTED_SECURITY_DETAIL]");
  assertThat(result.suspicious()).isTrue();
 }
}
