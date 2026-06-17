package com.sparta.one_stop.domain.point.audit;

import com.sparta.one_stop.global.audit.AdminAuditLog;
import com.sparta.one_stop.global.audit.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 감사 로그 비동기 저장기
 *
 * "캡처는 동기, 저장은 비동기" — ThreadLocal 증발 방지
 *
 *   [문제] PointAuditAspect의 @AfterReturning 어드바이스에 @Async가 붙어,
 *          어드바이스가 별도 스레드에서 실행됨
 *          → getCurrentRequest()/resolveActor()가 ThreadLocal(RequestContext,
 *            SecurityContext)을 읽지만 비동기 스레드엔 전파 안 됨 (증발)
 *          → actor=SYSTEM, clientIp=null 로 조용히 유실
 *
 *   [해결] 어드바이스는 동기로 실행(요청 스레드)하며 컨텍스트를 캡처해
 *          완성된 AdminAuditLog를 만든 뒤, 이 Writer에 비동기 저장만 위임.
 *          Writer는 ThreadLocal을 절대 읽지 않는다.
 *
 *
 * self-invocation 회피를 위해 Aspect와 별도 Bean으로 분리.
 * 전용 executor(eventExecutor) 사용으로 다른 비동기 작업과 큐 격리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointAuditWriter {

    private final AdminAuditLogRepository auditRepository;

    @Async("eventExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AdminAuditLog auditLog) {
        persistInternal(auditLog, "비동기");
    }

    /**
     * 비동기 executor가 작업을 거부한 경우 요청 스레드에서 최후의 수단으로 저장한다.
     * 별도 Bean 프록시를 통해 호출되므로 REQUIRES_NEW가 적용된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistSynchronously(AdminAuditLog auditLog) {
        persistInternal(auditLog, "동기 fallback");
    }

    private void persistInternal(AdminAuditLog auditLog, String mode) {
        try {
            auditRepository.save(auditLog);
        } catch (Exception e) {
            // 감사 저장 실패가 비즈니스 흐름을 깨뜨리지는 않되, 운영 로그에는 반드시 남긴다.
            log.error("[POINT_AUDIT] {} 저장 실패 — action={}, result={}",
                mode, auditLog.getAction(), auditLog.getResult(), e);
        }
    }
}
