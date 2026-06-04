package com.sparta.one_stop.domain.point.audit;

import com.sparta.one_stop.global.audit.AdminAuditLog;
import com.sparta.one_stop.global.audit.AdminAuditLogRepository;
import com.sparta.one_stop.global.security.AuthUser;
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

/**
 * 포인트 도메인 감사 로그 AOP — CodeRabbit 리뷰 대응 전면 수정본
 *
 * <p><b>v1 → v2 변경 사항</b>
 * <ol>
 *   <li><b>[CRITICAL]</b> {@code adminId}/{@code adminUsername} 채움 — SecurityContext에서 추출,
 *       시스템 이벤트는 SYSTEM_ACTOR_ID 사용</li>
 *   <li><b>[MAJOR]</b> 실패 액션 코드 통일 — 메서드명→정규 액션 매핑 테이블 사용</li>
 *   <li><b>[MAJOR]</b> 필드별 길이 truncate — args/errorDetail/userAgent 등 길이 초과 방지</li>
 *   <li><b>[MAJOR]</b> 시스템 이벤트 명시 처리 — 비-HTTP 컨텍스트에서도 정상 기록</li>
 *   <li><b>[MINOR]</b> 민감 정보 마스킹 강화</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PointAuditAspect {

    private final AdminAuditLogRepository auditRepository;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  ★ [수정] 메서드명 → 정규 액션 매핑 테이블
    //
    //  성공/실패 모두 같은 액션 코드를 쓰도록 통일.
    //  - 이전: 실패 시 "POINT_" + 메서드명.toUpperCase()
    //    → usePoint → POINT_USEPOINT
    //    → refundPointByOrder → POINT_REFUNDPOINTBYORDER
    //    → 성공 로그(POINT_USE)와 다른 버킷 → 집계 깨짐
    //  - 현재: 매핑 테이블로 통일
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private static final Map<String, String> METHOD_TO_ACTION = Map.of(
        "chargePoint",         "POINT_CHARGE",
        "usePoint",            "POINT_USE",
        "earnPoint",           "POINT_EARN",
        "refundPointByOrder",  "POINT_REFUND",
        "refundPoint",         "POINT_REFUND",
        "expirePoints",        "POINT_EXPIRE"
    );

    /** 매핑 테이블에 없는 메서드의 기본 액션 코드 */
    private static final String DEFAULT_ACTION = "POINT_UNKNOWN";

    // 필드 길이 상한 (AdminAuditLog 엔티티 컬럼 정의와 일치)
    private static final int MAX_ARGS_LENGTH = 500;
    private static final int MAX_ERROR_DETAIL_LENGTH = 500;
    private static final int MAX_USER_AGENT_LENGTH = 255;
    private static final int MAX_METHOD_NAME_LENGTH = 200;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  성공 케이스 — 메서드별 정규 액션 코드
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
    //  실패 케이스 — 매핑 테이블로 정규 액션 코드 통일
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @AfterThrowing(
        pointcut = "execution(* com.sparta.one_stop.domain.point.service.PointService.*(..))",
        throwing = "ex")
    @Async
    public void auditFailure(JoinPoint jp, Throwable ex) {
        // ★ [수정] 메서드명→정규 액션 매핑 (성공 로그와 같은 액션 코드 사용)
        String methodName = jp.getSignature().getName();
        String action = METHOD_TO_ACTION.getOrDefault(methodName, DEFAULT_ACTION);

        String errorDetail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        record(action, jp, "FAILURE", errorDetail);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  공통 기록 로직 — CodeRabbit 지적사항 일괄 반영
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private void record(String action, JoinPoint jp, String result, String errorDetail) {
        try {
            // ── HTTP 컨텍스트 수집 ──
            HttpServletRequest req = getCurrentRequest();
            String clientIp = req != null ? extractIp(req) : "SYSTEM";
            String userAgent = req != null ? req.getHeader("User-Agent") : "SCHEDULER";

            // ── ★ [CRITICAL 수정] Actor 정보 추출 ──
            //   SecurityContext가 비어있으면 시스템 이벤트로 처리
            ActorInfo actor = resolveActor();

            // ── ★ [MAJOR 수정] 필드별 truncate (저장 실패 방어) ──
            AdminAuditLog auditLog = AdminAuditLog.builder()
                .action(action)
                .targetResource("Point")
                .adminId(actor.id())                 // ★ 필수 필드 채움
                .adminUsername(actor.username())     // ★ 필수 필드 채움
                .methodName(truncate(jp.getSignature().toShortString(), MAX_METHOD_NAME_LENGTH))
                .args(truncate(safeArgs(jp.getArgs()), MAX_ARGS_LENGTH))
                .result(result)
                .errorDetail(truncate(errorDetail, MAX_ERROR_DETAIL_LENGTH))
                .clientIp(clientIp)
                .userAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH))
                .occurredAt(LocalDateTime.now())
                .build();

            auditRepository.save(auditLog);
        } catch (Exception e) {
            // 감사 로그 실패가 본 흐름을 막아서는 안 됨
            // 단, 이전과 달리 로그 레벨을 ERROR로 + 원인 명확히
            log.error("[AUDIT_ASPECT] 감사 로그 기록 실패 — 본 로직은 계속 진행. action={}, result={}, cause={}",
                action, result, e.getMessage(), e);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Actor 추출 — SecurityContext or 시스템 이벤트
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 현재 행위자 정보 추출
     *
     * <p>우선순위:
     * <ol>
     *   <li>SecurityContext에서 AuthUser → (userId, "user:{id}")</li>
     *   <li>인증 정보 없음 → SYSTEM_ACTOR_ID, SYSTEM_ACTOR_USERNAME (스케줄러/배치)</li>
     * </ol>
     */
    private ActorInfo resolveActor() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())
                && auth.getPrincipal() instanceof AuthUser authUser) {

                Long userId = authUser.getId();
                // username 형식: "user:{userId}" — 추후 email 조회로 확장 가능
                String username = "user:" + userId;
                return new ActorInfo(userId, username);
            }
        } catch (Exception e) {
            log.warn("[AUDIT_ASPECT] Actor 추출 실패 — 시스템 이벤트로 처리: {}", e.getMessage());
        }

        // ★ 시스템 이벤트 — 스케줄러/배치/익명 호출
        return new ActorInfo(
            AdminAuditLog.SYSTEM_ACTOR_ID,
            AdminAuditLog.SYSTEM_ACTOR_USERNAME
        );
    }

    private record ActorInfo(Long id, String username) {}

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  유틸리티
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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
        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return req.getRemoteAddr();
    }

    /**
     * ★ [수정] 필드별 길이 truncate
     *
     * <p>null-safe + "..." suffix로 절단 표시
     */
    private String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    /**
     * 메서드 인자 직렬화 + 민감 정보 마스킹
     */
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
                // 개별 인자도 너무 길면 자르기 (전체 args 길이 제한과 별개)
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
}
