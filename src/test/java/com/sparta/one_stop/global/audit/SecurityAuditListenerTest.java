package com.sparta.one_stop.global.audit;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class SecurityAuditListenerTest {
 @Test void 저장실패는_비즈니스흐름으로_전파하지않는다(){
  SecurityAuditWriter writer=mock(SecurityAuditWriter.class);doThrow(new IllegalStateException("db down")).when(writer).save(any());
  var event=new PreparedSecurityAuditEvent(SecurityAuditEventType.LOGIN_FAILED,SecuritySeverity.MEDIUM,SecurityAuditEventType.Category.AUTH,null,null,null,null,null,"FAILURE",null,null,null,null,null,null,null,null,null,null,false,LocalDateTime.now());
  var registry=new SimpleMeterRegistry();
  assertThatCode(()->new SecurityAuditListener(writer,registry).handle(event)).doesNotThrowAnyException();
  assertThat(registry.get("security.audit.save.failures").counter().count()).isEqualTo(1.0);
 }
}
