package com.sparta.one_stop.domain.point.audit;

import com.sparta.one_stop.global.audit.AdminAuditLog;
import com.sparta.one_stop.global.audit.AdminAuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointAuditWriterTest {

    @Mock
    private AdminAuditLogRepository auditRepository;

    @InjectMocks
    private PointAuditWriter pointAuditWriter;

    @Test
    @DisplayName("persist 성공 - 포인트 감사 로그를 저장한다")
    void persist_success_saveAuditLog() {
        // given
        AdminAuditLog auditLog = mock(AdminAuditLog.class);

        // when
        pointAuditWriter.persist(auditLog);

        // then
        verify(auditRepository).save(auditLog);
    }

    @Test
    @DisplayName("persist 성공 - 감사 로그 저장 실패가 발생해도 예외를 전파하지 않는다")
    void persist_success_doNotThrow_whenSaveFails() {
        // given
        AdminAuditLog auditLog = mock(AdminAuditLog.class);

        when(auditLog.getAction()).thenReturn("POINT_CHARGE");
        when(auditLog.getResult()).thenReturn("FAILURE");

        when(auditRepository.save(auditLog))
            .thenThrow(new RuntimeException("audit save failed"));

        // when & then
        assertThatCode(() -> pointAuditWriter.persist(auditLog))
            .doesNotThrowAnyException();

        verify(auditRepository).save(auditLog);
    }

    @Test
    @DisplayName("persist 트랜잭션 설정 - REQUIRES_NEW 트랜잭션을 사용한다")
    void persist_hasRequiresNewTransaction() throws Exception {
        // given
        Method method = PointAuditWriter.class.getMethod(
            "persist",
            AdminAuditLog.class
        );

        // when
        Transactional transactional = method.getAnnotation(Transactional.class);

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation())
            .isEqualTo(Propagation.REQUIRES_NEW);
    }

}
