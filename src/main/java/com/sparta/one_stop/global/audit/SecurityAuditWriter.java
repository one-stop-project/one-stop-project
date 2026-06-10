package com.sparta.one_stop.global.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보안 감사 로그 비동기 저장기
 *
 * EDA 리팩터링 핵심 — "캡처는 동기, 저장은 비동기" 분리
 *
 *   [문제] 기존 SecurityAuditService.record()가 @Async 안에서
 *          SecurityContextHolder / RequestContextHolder(ThreadLocal)를 읽음
 *          → 비동기 스레드엔 ThreadLocal이 전파 안 됨 (증발)
 *          → actor/ip가 null → 이를 우회하려 AT에 email을 박음 (PII 노출)
 *
 *   [해결] 이 Bean은 ThreadLocal을 절대 읽지 않는다.
 *          이미 모든 정보가 채워진 SecurityAuditLog를 받아 save만 수행.
 *          컨텍스트 캡처는 호출 측(요청 스레드, 동기)에서 끝낸다.
 *
 *
 * self-invocation 회피: SecurityAuditService와 별도 Bean으로 분리해
 * {@code @Async} 프록시가 정상 동작하도록 한다.
 *
 * Fail-Safe: 저장 실패가 본 흐름(이미 커밋된 요청)에 영향 주지 않도록
 * 예외를 삼키고 로그만 남긴다. {@code @Async} 스레드의 예외는 AsyncConfig의
 * AsyncUncaughtExceptionHandler로도 수집된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAuditWriter {

    private final SecurityAuditLogRepository repository;

    /**
     * 완성된 감사 로그를 비동기로 저장 — ThreadLocal 접근 없음
     *
     * @param logEntry 모든 필드가 채워진 엔티티 (요청 스레드에서 빌드 완료)
     */
    @Async("eventExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(SecurityAuditLog logEntry) {
        try {
            repository.save(logEntry);

            if (logEntry.getEventType() != null
                && logEntry.getEventType().getSeverity() == SecurityAuditEventType.Severity.CRITICAL) {
                log.error("[SECURITY_CRITICAL] {} — user={}, ip={}, target={}/{}, msg={}",
                    logEntry.getEventType().name(),
                    logEntry.getActorUserId(), logEntry.getClientIp(),
                    logEntry.getTargetResource(), logEntry.getTargetId(),
                    logEntry.getErrorMessage());
                // TODO: SlackNotifier.sendUrgent(...) — 운영 알림
            }
        } catch (Exception e) {
            // 감사 로그 저장 실패가 본 흐름을 막지 않음 (Fail-Safe)
            log.error("[SECURITY_AUDIT] 비동기 저장 실패 (eventType={}), 본 로직 영향 없음",
                logEntry.getEventType(), e);
        }
    }
}
