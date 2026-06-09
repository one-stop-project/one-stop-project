package com.sparta.one_stop.domain.point.audit;

import com.sparta.one_stop.global.audit.AdminAuditLog;
import com.sparta.one_stop.global.audit.AdminAuditLogRepository;
import com.sparta.one_stop.global.security.AuthUser;
import com.sparta.one_stop.global.util.ClientIpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PointAuditAspect {

    // 메서드명 → 정규 액션 매핑 (성공/실패 일관성)
    private static final Map<String, String> METHOD_TO_ACTION = Map.of(
        "chargePoint",        "POINT_CHARGE",
        "usePoint",           "POINT_USE",
        "refundPointByOrder", "POINT_REFUND"
    );
    private static final String DEFAULT_ACTION = "POINT_UNKNOWN";
    // 필드 길이 상한 (AdminAuditLog 엔티티 컬럼 정의와 일치)
    private static final int MAX_ARGS_LENGTH = 500;
    private static final int MAX_ERROR_DETAIL_LENGTH = 500;
    private static final int MAX_USER_AGENT_LENGTH = 255;
    private static final int MAX_METHOD_NAME_LENGTH = 200;
    private static final int MAX_CLIENT_IP_LENGTH = 45;  // ★ 추가 — IPv6 호환
    private final AdminAuditLogRepository auditRepository;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  성공 케이스 — PointService에 실제 존재하는 메서드만 가리킴
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
        pointcut = "execution(* com.sparta.one_stop.domain.point.service.PointService.refundPointByOrder(..))")
    @Async
    public void auditRefund(JoinPoint jp) {
        record("POINT_REFUND", jp, "SUCCESS", null);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  실패 케이스
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @AfterThrowing(
        pointcut = "execution(* com.sparta.one_stop.domain.point.service.PointService.*(..))",
        throwing = "ex")
    @Async
    public void auditFailure(JoinPoint jp, Throwable ex) {
        String methodName = jp.getSignature().getName();
        String action = METHOD_TO_ACTION.getOrDefault(methodName, DEFAULT_ACTION);
        String errorDetail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        record(action, jp, "FAILURE", errorDetail);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  공통 기록 로직
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private void record(String action, JoinPoint jp, String result, String errorDetail) {
        try {
            HttpServletRequest req = getCurrentRequest();
            String clientIp = req != null ? ClientIpExtractor.extract(req) : "SYSTEM";
            String userAgent = req != null ? req.getHeader("User-Agent") : "SCHEDULER";

            ActorInfo actor = resolveActor();

            AdminAuditLog auditLog = AdminAuditLog.builder()
                .action(action)
                .targetResource("Point")
                .adminId(actor.id())
                .adminUsername(actor.username())
                .methodName(truncate(jp.getSignature().toShortString(), MAX_METHOD_NAME_LENGTH))
                .args(truncate(safeArgs(jp.getArgs()), MAX_ARGS_LENGTH))
                .result(result)
                .errorDetail(truncate(errorDetail, MAX_ERROR_DETAIL_LENGTH))
                .clientIp(truncate(clientIp, MAX_CLIENT_IP_LENGTH))         // ★ 추가
                .userAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH))
                .occurredAt(LocalDateTime.now())
                .build();

            auditRepository.save(auditLog);
        } catch (Exception e) {
            log.error("[AUDIT_ASPECT] 감사 로그 기록 실패 — action={}, result={}, cause={}",
                action, result, e.getMessage(), e);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Actor 추출 — 동일 (변경 없음)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private ActorInfo resolveActor() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())
                && auth.getPrincipal() instanceof AuthUser authUser) {

                Long userId = authUser.userId();
                String username = "user:" + userId;
                return new ActorInfo(userId, username);
            }
        } catch (Exception e) {
            log.warn("[AUDIT_ASPECT] Actor 추출 실패 — 시스템 이벤트로 처리: {}", e.getMessage());
        }

        return new ActorInfo(
            AdminAuditLog.SYSTEM_ACTOR_ID,
            AdminAuditLog.SYSTEM_ACTOR_USERNAME
        );
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  유틸리티 — 동일 (변경 없음)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private HttpServletRequest getCurrentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    private String safeArgs(Object[] args) {
        if (args == null || args.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            Object arg = args[i];
            if (arg == null) {
                sb.append("null");
            } else if (isSensitive(arg)) {
                sb.append(arg.getClass().getSimpleName()).append("[masked]");
            } else if (arg.getClass().getSimpleName().endsWith("Request")) {
                sb.append(arg.getClass().getSimpleName());
            } else {
                String s = arg.toString();
                sb.append(s.length() > 50 ? s.substring(0, 47) + "..." : s);
            }
        }
        return sb.append("]").toString();
    }

    private boolean isSensitive(Object arg) {
        if (arg == null) return false;
        String name = arg.getClass().getSimpleName().toLowerCase();
        return name.contains("password") || name.contains("credential") || name.contains("secret");
    }

    private record ActorInfo(Long id, String username) {}
}
