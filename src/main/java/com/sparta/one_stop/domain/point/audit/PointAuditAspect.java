package com.sparta.one_stop.domain.point.audit;

import com.sparta.one_stop.global.audit.AdminAuditLog;
import com.sparta.one_stop.global.audit.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;

// 포인트 도메인 감사 로그 AOP
// 설계의도
// 1. PointService의 모든 변경 메서드를 자동 감사
// 2. 비동기로 기록 -> 본 트랜잭션 성능 영향 없음
// 3. 본 트랜잭션 성공 후에만 기록
// 4. 실패도 기록 : 공격 시도 패턴 분석용

// DB트리거와의 관계
// 1. DB 트리거 : 모든 UPDATE를 무조건 기록 (DB 레이어 진실)
// 2. AOP 감사 : 애플리케이션 의도를 기록(어떤 비즈니스 행위였나)

//─ 두 로그를 대조하면 ─
// *   DB 트리거 ✅ + AOP 감사 ❌ → DB 직접 조작 의심!
// *   DB 트리거 ❌ + AOP 감사 ✅ → 트랜잭션 롤백 (정상)
// *   둘 다 ✅                    → 정상 흐름


@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PointAuditAspect {

    private final AdminAuditLogRepository auditRepository;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  성공 케이스
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @AfterReturning(
        pointcut = "execution(* com.sparta.one_stop.domain.point.service.PointService.chargePoint(..))",
        returning = "result")
    @Async
    public void auditCharge(JoinPoint jp, Object result) {
        record("POINT_CHARGE", jp, "SUCCESS", null);
    }

    @AfterReturning(
        pointcut = "execution(* com.sparta.one_stop.domain.point.service.PointService.usePoint(..))")
    @Async
    public void auditUse(JoinPoint jp) {
        record("POINT_USE", jp, "SUCCESS", null);
    }

    @AfterReturning(
        pointcut = "execution(* com.sparta.one_stop.domain.point.service.PointService.earnPoint(..))")
    @Async
    public void auditEarn(JoinPoint jp) {
        record("POINT_EARN", jp, "SUCCESS", null);
    }

    @AfterReturning(
        pointcut = "execution(* com.sparta.one_stop.domain.point.service.PointService.refundPointByOrder(..))")
    @Async
    public void auditRefund(JoinPoint jp) {
        record("POINT_REFUND", jp, "SUCCESS", null);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  실패 케이스 — 공격 시도 분석용
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @AfterThrowing(
        pointcut = "execution(* com.sparta.one_stop.domain.point.service.PointService.*(..))",
        throwing = "ex")
    @Async
    public void auditFailure(JoinPoint jp, Throwable ex) {
        String action = "POINT_" + jp.getSignature().getName().toUpperCase();
        record(action, jp, "FAILURE", ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  공통 기록 로직
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private void record(String action, JoinPoint jp, String result, String errorDetail) {
        try {
            // HTTP 컨텍스트 정보 (스케줄러 등 비-HTTP 호출에서는 null)
            HttpServletRequest req = getCurrentRequest();
            String clientIp = req != null ? extractIp(req) : "SYSTEM";
            String userAgent = req != null ? req.getHeader("User-Agent") : "SCHEDULER";

            AdminAuditLog log = AdminAuditLog.builder()
                .action(action)
                .targetResource("Point")
                .methodName(jp.getSignature().toShortString())
                .args(safeArgs(jp.getArgs()))
                .result(result)
                .errorDetail(errorDetail)
                .clientIp(clientIp)
                .userAgent(userAgent)
                .occurredAt(LocalDateTime.now())
                .build();

            auditRepository.save(log);
        } catch (Exception e) {
            // 감사 로그 실패가 본 흐름을 막아서는 안 됨
            log.error("감사 로그 기록 실패 (action={})", action, e);
        }
    }

    private HttpServletRequest getCurrentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    private String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private String safeArgs(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        // 민감 정보(password 등) 마스킹 — 포인트 메서드는 보통 userId, amount만
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            Object arg = args[i];
            if (arg == null) sb.append("null");
            else if (arg.getClass().getSimpleName().endsWith("Request")) sb.append(arg.getClass().getSimpleName());
            else sb.append(arg.toString());
        }
        return sb.append("]").toString();
    }
}

