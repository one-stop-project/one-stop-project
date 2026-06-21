package com.sparta.one_stop.global.audit;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditCryptoServiceTest {
 private final SecurityAuditCryptoService crypto=new SecurityAuditCryptoService("hmac-secret-for-test-over-32-bytes","aes-secret-for-test-over-32-bytes","v1");
 @Test void ip는_버전이_포함된_AES_암호문과_해시_대역으로_분리된다(){
  String encrypted=crypto.encryptIp("123.45.67.89");
  assertThat(encrypted).startsWith("v1:").doesNotContain("123.45.67.89");
  assertThat(crypto.hmacSha256("123.45.67.89")).hasSize(64).isEqualTo(crypto.hmacSha256("123.45.67.89"));
  assertThat(crypto.toIpPrefix("123.45.67.89")).isEqualTo("123.45.67.0/24");
 }
}
