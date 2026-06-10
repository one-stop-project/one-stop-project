package com.sparta.one_stop.global.audit;

import com.sparta.one_stop.global.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final SecurityAuditLogRepository repository;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  기록 API — 다양한 형태로 호출 가능
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 기본 기록 — 성공 케이스
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(SecurityAuditEventType eventType) {
        record(SecurityAuditEvent.builder()
            .eventType(eventType)
            .result("SUCCESS")
            .build());
    }

    /**
     * 대상 리소스가 있는 성공 케이스
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(SecurityAuditEventType eventType, String targetResource, String targetId) {
        record(SecurityAuditEvent.builder()
            .eventType(eventType)
            .result("SUCCESS")
            .targetResource(targetResource)
            .targetId(targetId)
            .build());
    }

    /**
     * 실패 케이스
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(SecurityAuditEventType eventType, String errorCode, String errorMessage) {
        record(SecurityAuditEvent.builder()
            .eventType(eventType)
            .result("FAILURE")
            .errorCode(errorCode)
            .errorMessage(safeTruncate(errorMessage, 500))
            .build());
    }

    /**
     * 모든 정보 포함 — 가장 상세한 기록
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SecurityAuditEvent event) {
        try {
            // HTTP 컨텍스트에서 자동 수집
            HttpContext ctx = captureHttpContext();
            ActorContext actor = captureActorContext();

            SecurityAuditLog log = SecurityAuditLog.builder()
                .eventType(event.eventType())
                .actorUserId(actor.userId() != null ? actor.userId() : event.actorUserId())
                .actorEmail(actor.email() != null ? actor.email() : event.actorEmail())
                .actorRole(actor.role() != null ? actor.role() : event.actorRole())
                .targetResource(event.targetResource())
                .targetId(event.targetId())
                .result(event.result())
                .errorCode(event.errorCode())
                .errorMessage(event.errorMessage())
                .methodName(event.methodName())
                .methodArgs(event.methodArgs())
                .clientIp(ctx.ip())
                .userAgent(ctx.userAgent())
                .requestUri(ctx.requestUri())
                .traceId(ctx.traceId())
                .metadata(event.metadata())
                .occurredAt(event.occurredAt() != null ? event.occurredAt() : LocalDateTime.now())
                .build();

            repository.save(log);

            // CRITICAL 이벤트는 즉시 콘솔 로그도 남김 (Slack 연동 가능)
            if (event.eventType().getSeverity() == SecurityAuditEventType.Severity.CRITICAL) {
                SecurityAuditService.log.error("[SECURITY_CRITICAL] {} — user={}, ip={}, target={}/{}, msg={}",
                    event.eventType().name(),
                    actor.userId(), ctx.ip(),
                    event.targetResource(), event.targetId(),
                    event.errorMessage());
                // TODO: SlackNotifier.sendUrgent(...) — 운영 알림
            }

        } catch (Exception e) {
            // 감사 로그 실패가 본 흐름을 막아서는 안 됨
            SecurityAuditService.log.error("[SECURITY_AUDIT] 기록 실패 (event={}), 본 로직은 계속 진행",
                event.eventType(), e);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  컨텍스트 자동 캡처
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private HttpContext captureHttpContext() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            // 스케줄러, 배치 등 비-HTTP 컨텍스트
            return new HttpContext("SYSTEM", "SCHEDULER", null, null);
        }
        HttpServletRequest req = sra.getRequest();
        return new HttpContext(
            extractIp(req),
            req.getHeader("User-Agent"),
            req.getRequestURI(),
            req.getHeader("X-Trace-Id")  // 분산 추적 헤더 (선택)
        );
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

    private ActorContext captureActorContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return new ActorContext(null, null, null);
        }

        if (auth.getPrincipal() instanceof AuthUser authUser) {
            return new ActorContext(
                authUser.userId(),
                authUser.email(),  // JWT email claim에서 추출 (감사 행위자 식별)
                authUser.role() != null ? authUser.role().name() : null
            );
        }
        return new ActorContext(null, null, null);
    }

    private String safeTruncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  내부 record
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private record HttpContext(String ip, String userAgent, String requestUri, String traceId) {}
    private record ActorContext(Long userId, String email, String role) {}
}
