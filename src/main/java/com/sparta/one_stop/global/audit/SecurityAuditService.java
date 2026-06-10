package com.sparta.one_stop.global.audit;

import com.sparta.one_stop.global.security.AuthUser;
import com.sparta.one_stop.global.util.ClientIpExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final SecurityAuditWriter writer;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  기록 API — 동기 진입점 (요청 스레드에서 호출)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public void recordSuccess(SecurityAuditEventType eventType) {
        record(SecurityAuditEvent.builder()
            .eventType(eventType)
            .result("SUCCESS")
            .build());
    }

    public void recordSuccess(SecurityAuditEventType eventType, String targetResource, String targetId) {
        record(SecurityAuditEvent.builder()
            .eventType(eventType)
            .result("SUCCESS")
            .targetResource(targetResource)
            .targetId(targetId)
            .build());
    }

    public void recordFailure(SecurityAuditEventType eventType, String errorCode, String errorMessage) {
        record(SecurityAuditEvent.builder()
            .eventType(eventType)
            .result("FAILURE")
            .errorCode(errorCode)
            .errorMessage(safeTruncate(errorMessage, 500))
            .build());
    }

    public void record(SecurityAuditEvent event) {
        try {
            // ── 1. 컨텍스트 캡처 (동기 — ThreadLocal 살아있는 시점) ──
            HttpContext ctx = captureHttpContext();
            ActorContext actor = captureActorContext();

            // ── 2. 엔티티 완성 (모든 값 채움) ──
            SecurityAuditLog logEntry = SecurityAuditLog.builder()
                .eventType(event.eventType())
                .actorUserId(actor.userId() != null ? actor.userId() : event.actorUserId())
                // email은 자동 캡처 안 함(PII) — 발행자가 명시한 경우에만 기록
                .actorEmail(event.actorEmail())
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

            // ── 3. 비동기 저장 위임 (Writer는 ThreadLocal 안 읽음) ──
            writer.persist(logEntry);

        } catch (Exception e) {
            // 캡처/위임 단계 실패가 본 흐름을 막아서는 안 됨 (Fail-Safe)
            log.error("[SECURITY_AUDIT] 기록 준비 실패 (event={}), 본 로직은 계속 진행",
                event.eventType(), e);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  컨텍스트 캡처 (동기 — 요청 스레드 ThreadLocal)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private HttpContext captureHttpContext() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            // 스케줄러, 배치, 비동기 등 비-HTTP 컨텍스트
            return new HttpContext("SYSTEM", "SCHEDULER", null, null);
        }
        HttpServletRequest req = sra.getRequest();
        return new HttpContext(
            ClientIpExtractor.extract(req),   // 공통 유틸 사용 (중복 제거)
            req.getHeader("User-Agent"),
            req.getRequestURI(),
            req.getHeader("X-Trace-Id")
        );
    }

    private ActorContext captureActorContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return new ActorContext(null, null);
        }
        if (auth.getPrincipal() instanceof AuthUser authUser) {
            // PII 최소화: userId/role만 캡처 (email 미보유)
            return new ActorContext(
                authUser.userId(),
                authUser.role() != null ? authUser.role().name() : null
            );
        }
        return new ActorContext(null, null);
    }

    private String safeTruncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  내부 record
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private record HttpContext(String ip, String userAgent, String requestUri, String traceId) {}
    private record ActorContext(Long userId, String role) {}
}
