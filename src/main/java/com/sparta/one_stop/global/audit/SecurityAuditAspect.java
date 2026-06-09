package com.sparta.one_stop.global.audit;

import com.sparta.one_stop.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * {@link Audited} 어노테이션 처리 AOP
 *
 * 동작
 *
 *   {@code @Audited}가 붙은 메서드를 가로챔
 *   {@code proceed()}로 실제 메서드 실행
 *   성공 → SUCCESS 기록
 *   예외 → FAILURE + 에러 정보 기록 후 예외 재던짐
 *
 *
 * 왜 @Around인가?
 * @AfterReturning만 쓰면 실패 케이스를 못 잡음.
 * @Around는 정상/예외 양쪽 흐름을 모두 캡처 가능.
 *
 * 예외 재던짐 보장
 * 감사 로깅이 실패한다고 본 예외를 삼키면 안 됨 → finally에서 throw.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SecurityAuditAspect {

    private final SecurityAuditService auditService;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(audited)")
    public Object aroundAudited(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Object[] args = pjp.getArgs();

        // 대상 ID 추출 (SpEL)
        String targetId = extractTargetId(audited.targetIdExpr(), method, args);
        String targetResource = audited.targetResource().isEmpty() ? null : audited.targetResource();
        String methodName = signature.toShortString();
        String methodArgs = audited.recordArgs() ? safeArgs(args) : "[masked]";

        try {
            // 실제 메서드 실행
            Object result = pjp.proceed();

            // 성공 기록
            auditService.record(SecurityAuditEvent.builder()
                    .eventType(audited.value())
                    .result("SUCCESS")
                    .targetResource(targetResource)
                    .targetId(targetId)
                    .methodName(methodName)
                    .methodArgs(methodArgs)
                    .build());

            return result;

        } catch (Throwable ex) {
            // 실패 기록 — 그 후 예외 재던짐
            try {
                String errorCode = ex instanceof CustomException ce
                        ? ce.getErrorCode().name()
                        : ex.getClass().getSimpleName();

                auditService.record(SecurityAuditEvent.builder()
                        .eventType(audited.value())
                        .result("FAILURE")
                        .errorCode(errorCode)
                        .errorMessage(safeTruncate(ex.getMessage(), 500))
                        .targetResource(targetResource)
                        .targetId(targetId)
                        .methodName(methodName)
                        .methodArgs(methodArgs)
                        .build());
            } catch (Exception auditEx) {
                log.error("[AUDIT_ASPECT] 실패 기록 중 예외 발생 — 본 예외는 그대로 던집니다", auditEx);
            }
            throw ex;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  SpEL 평가 — targetIdExpr 처리
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private String extractTargetId(String expr, Method method, Object[] args) {
        if (expr == null || expr.isEmpty()) return null;
        try {
            EvaluationContext ctx = new StandardEvaluationContext();
            String[] paramNames = paramNameDiscoverer.getParameterNames(method);
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    ctx.setVariable(paramNames[i], args[i]);
                }
            }
            Expression expression = parser.parseExpression(expr);
            Object value = expression.getValue(ctx);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("[AUDIT_ASPECT] SpEL 평가 실패 (expr={}): {}", expr, e.getMessage());
            return null;
        }
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

    private String safeTruncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
