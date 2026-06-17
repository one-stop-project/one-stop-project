package com.sparta.one_stop.domain.point.audit;

import com.sparta.one_stop.global.audit.AdminAuditLog;
import com.sparta.one_stop.global.util.ClientIpExtractor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointAuditAspectTest {

    @Mock
    private ClientIpExtractor clientIpExtractor;

    @Mock
    private PointAuditWriter pointAuditWriter;

    @InjectMocks
    private PointAuditAspect pointAuditAspect;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("auditCharge 성공 - chargePoint 성공 감사 로그를 기록한다")
    void auditCharge_success_recordPointChargeSuccessLog() {
        // given
        JoinPoint joinPoint = successJoinPoint(
            "PointService.chargePoint(..)",
            1L,
            5000
        );

        // when
        pointAuditAspect.auditCharge(
            joinPoint,
            null
        );

        // then
        ArgumentCaptor<AdminAuditLog> captor =
            ArgumentCaptor.forClass(AdminAuditLog.class);

        verify(pointAuditWriter).persist(captor.capture());

        AdminAuditLog auditLog = captor.getValue();

        assertThat(auditLog.getAction()).isEqualTo("POINT_CHARGE");
        assertThat(auditLog.getTargetResource()).isEqualTo("Point");
        assertThat(auditLog.getResult()).isEqualTo("SUCCESS");
        assertThat(auditLog.getErrorDetail()).isNull();
        assertThat(auditLog.getMethodName()).isEqualTo("PointService.chargePoint(..)");
        assertThat(auditLog.getArgs()).contains("1", "5000");
        assertThat(auditLog.getClientIp()).isEqualTo("SYSTEM");
        assertThat(auditLog.getUserAgent()).isEqualTo("SCHEDULER");
        assertThat(auditLog.getAdminId()).isEqualTo(AdminAuditLog.SYSTEM_ACTOR_ID);
        assertThat(auditLog.getAdminUsername()).isEqualTo(AdminAuditLog.SYSTEM_ACTOR_USERNAME);
        assertThat(auditLog.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("auditUse 성공 - usePoint 성공 감사 로그를 기록한다")
    void auditUse_success_recordPointUseSuccessLog() {
        // given
        JoinPoint joinPoint = successJoinPoint(
            "PointService.usePoint(..)",
            1L,
            "order",
            1000
        );

        // when
        pointAuditAspect.auditUse(joinPoint);

        // then
        ArgumentCaptor<AdminAuditLog> captor =
            ArgumentCaptor.forClass(AdminAuditLog.class);

        verify(pointAuditWriter).persist(captor.capture());

        AdminAuditLog auditLog = captor.getValue();

        assertThat(auditLog.getAction()).isEqualTo("POINT_USE");
        assertThat(auditLog.getTargetResource()).isEqualTo("Point");
        assertThat(auditLog.getResult()).isEqualTo("SUCCESS");
        assertThat(auditLog.getErrorDetail()).isNull();
        assertThat(auditLog.getMethodName()).isEqualTo("PointService.usePoint(..)");
        assertThat(auditLog.getArgs()).contains("1", "order", "1000");
        assertThat(auditLog.getClientIp()).isEqualTo("SYSTEM");
        assertThat(auditLog.getUserAgent()).isEqualTo("SCHEDULER");
        assertThat(auditLog.getAdminId()).isEqualTo(AdminAuditLog.SYSTEM_ACTOR_ID);
        assertThat(auditLog.getAdminUsername()).isEqualTo(AdminAuditLog.SYSTEM_ACTOR_USERNAME);
        assertThat(auditLog.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("auditRefund 성공 - refundPointByOrder 성공 감사 로그를 기록한다")
    void auditRefund_success_recordPointRefundSuccessLog() {
        // given
        JoinPoint joinPoint = successJoinPoint(
            "PointService.refundPointByOrder(..)",
            "order"
        );

        // when
        pointAuditAspect.auditRefund(joinPoint);

        // then
        ArgumentCaptor<AdminAuditLog> captor =
            ArgumentCaptor.forClass(AdminAuditLog.class);

        verify(pointAuditWriter).persist(captor.capture());

        AdminAuditLog auditLog = captor.getValue();

        assertThat(auditLog.getAction()).isEqualTo("POINT_REFUND");
        assertThat(auditLog.getTargetResource()).isEqualTo("Point");
        assertThat(auditLog.getResult()).isEqualTo("SUCCESS");
        assertThat(auditLog.getErrorDetail()).isNull();
        assertThat(auditLog.getMethodName()).isEqualTo("PointService.refundPointByOrder(..)");
        assertThat(auditLog.getArgs()).contains("order");
        assertThat(auditLog.getClientIp()).isEqualTo("SYSTEM");
        assertThat(auditLog.getUserAgent()).isEqualTo("SCHEDULER");
        assertThat(auditLog.getAdminId()).isEqualTo(AdminAuditLog.SYSTEM_ACTOR_ID);
        assertThat(auditLog.getAdminUsername()).isEqualTo(AdminAuditLog.SYSTEM_ACTOR_USERNAME);
        assertThat(auditLog.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("auditFailure 성공 - 실패 메서드명이 매핑된 액션이면 해당 액션으로 실패 감사 로그를 기록한다")
    void auditFailure_success_recordMappedFailureLog() {
        // given
        JoinPoint joinPoint = failureJoinPoint(
            "usePoint",
            "PointService.usePoint(..)",
            1L,
            "order",
            1000
        );

        RuntimeException exception = new RuntimeException("포인트 사용 실패");

        // when
        pointAuditAspect.auditFailure(
            joinPoint,
            exception
        );

        // then
        ArgumentCaptor<AdminAuditLog> captor =
            ArgumentCaptor.forClass(AdminAuditLog.class);

        verify(pointAuditWriter).persist(captor.capture());

        AdminAuditLog auditLog = captor.getValue();

        assertThat(auditLog.getAction()).isEqualTo("POINT_USE");
        assertThat(auditLog.getTargetResource()).isEqualTo("Point");
        assertThat(auditLog.getResult()).isEqualTo("FAILURE");
        assertThat(auditLog.getErrorDetail()).isEqualTo("RuntimeException: 포인트 사용 실패");
        assertThat(auditLog.getMethodName()).isEqualTo("PointService.usePoint(..)");
        assertThat(auditLog.getArgs()).contains("1", "order", "1000");
        assertThat(auditLog.getClientIp()).isEqualTo("SYSTEM");
        assertThat(auditLog.getUserAgent()).isEqualTo("SCHEDULER");
        assertThat(auditLog.getAdminId()).isEqualTo(AdminAuditLog.SYSTEM_ACTOR_ID);
        assertThat(auditLog.getAdminUsername()).isEqualTo(AdminAuditLog.SYSTEM_ACTOR_USERNAME);
        assertThat(auditLog.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("auditFailure 성공 - 실패 메서드명이 매핑되지 않으면 POINT_UNKNOWN으로 기록한다")
    void auditFailure_success_recordUnknownFailureLog_whenMethodIsNotMapped() {
        // given
        JoinPoint joinPoint = failureJoinPoint(
            "unknownMethod",
            "PointService.unknownMethod(..)",
            1L
        );

        IllegalStateException exception = new IllegalStateException("알 수 없는 실패");

        // when
        pointAuditAspect.auditFailure(
            joinPoint,
            exception
        );

        // then
        ArgumentCaptor<AdminAuditLog> captor =
            ArgumentCaptor.forClass(AdminAuditLog.class);

        verify(pointAuditWriter).persist(captor.capture());

        AdminAuditLog auditLog = captor.getValue();

        assertThat(auditLog.getAction()).isEqualTo("POINT_UNKNOWN");
        assertThat(auditLog.getResult()).isEqualTo("FAILURE");
        assertThat(auditLog.getErrorDetail()).isEqualTo("IllegalStateException: 알 수 없는 실패");
        assertThat(auditLog.getMethodName()).isEqualTo("PointService.unknownMethod(..)");
    }

    @Test
    @DisplayName("감사 로그 기록 실패 - PointAuditWriter 예외가 발생해도 비즈니스 흐름에 예외를 전파하지 않는다")
    void audit_success_doNotThrow_whenPointAuditWriterFails() {
        // given
        JoinPoint joinPoint = successJoinPoint(
            "PointService.chargePoint(..)",
            1L,
            5000
        );

        doThrow(new RuntimeException("audit writer failed"))
            .when(pointAuditWriter)
            .persist(any(AdminAuditLog.class));

        // when & then
        assertThatCode(() -> pointAuditAspect.auditCharge(
            joinPoint,
            null
        )).doesNotThrowAnyException();

        verify(pointAuditWriter).persist(any(AdminAuditLog.class));
    }

    @Test
    @DisplayName("포인트컷 설정 - chargePoint/usePoint/refundPointByOrder 성공 포인트컷이 PointService 메서드를 대상으로 한다")
    void pointcut_successAdviceTargetsPointServiceMethods() throws Exception {
        // given
        Method auditCharge = PointAuditAspect.class.getMethod(
            "auditCharge",
            JoinPoint.class,
            Object.class
        );

        Method auditUse = PointAuditAspect.class.getMethod(
            "auditUse",
            JoinPoint.class
        );

        Method auditRefund = PointAuditAspect.class.getMethod(
            "auditRefund",
            JoinPoint.class
        );

        // when
        AfterReturning chargeAnnotation = auditCharge.getAnnotation(AfterReturning.class);
        AfterReturning useAnnotation = auditUse.getAnnotation(AfterReturning.class);
        AfterReturning refundAnnotation = auditRefund.getAnnotation(AfterReturning.class);

        // then
        assertThat(chargeAnnotation).isNotNull();
        assertThat(chargeAnnotation.pointcut())
            .contains("PointService.chargePoint");

        assertThat(useAnnotation).isNotNull();
        assertThat(useAnnotation.pointcut())
            .contains("PointService.usePoint");

        assertThat(refundAnnotation).isNotNull();
        assertThat(refundAnnotation.pointcut())
            .contains("PointService.refundPointByOrder");
    }

    @Test
    @DisplayName("포인트컷 설정 - 실패 포인트컷은 PointService 전체 메서드를 대상으로 한다")
    void pointcut_failureAdviceTargetsPointServiceMethods() throws Exception {
        // given
        Method auditFailure = PointAuditAspect.class.getMethod(
            "auditFailure",
            JoinPoint.class,
            Throwable.class
        );

        // when
        AfterThrowing afterThrowing = auditFailure.getAnnotation(AfterThrowing.class);

        // then
        assertThat(afterThrowing).isNotNull();
        assertThat(afterThrowing.pointcut())
            .contains("PointService.*");
        assertThat(afterThrowing.throwing())
            .isEqualTo("ex");
    }

    private JoinPoint successJoinPoint(
        String shortString,
        Object... args
    ) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn(shortString);
        when(joinPoint.getArgs()).thenReturn(args);

        return joinPoint;
    }

    private JoinPoint failureJoinPoint(
        String methodName,
        String shortString,
        Object... args
    ) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        Signature signature = mock(Signature.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn(methodName);
        when(signature.toShortString()).thenReturn(shortString);
        when(joinPoint.getArgs()).thenReturn(args);

        return joinPoint;
    }

}
