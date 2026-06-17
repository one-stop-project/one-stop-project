package com.sparta.one_stop.domain.point.audit;

import com.sparta.one_stop.global.audit.AdminAuditLog;
import com.sparta.one_stop.global.audit.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 감사 로그 저장기
 *
 * Aspect는 요청 스레드에서 사용자, IP, User-Agent 등 ThreadLocal 기반 정보를 캡처하고,
 * Writer는 완성된 AdminAuditLog를 별도 트랜잭션으로 저장한다.
 *
 * 감사 로그 유실 가능성을 줄이기 위해 비동기 executor를 사용하지 않고 동기 저장한다.
 * 감사 로그 저장은 REQUIRES_NEW 트랜잭션으로 분리하여 기존 비즈니스 트랜잭션과 독립적으로 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointAuditWriter {

    private final AdminAuditLogRepository auditRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AdminAuditLog auditLog) {
        try {
            auditRepository.save(auditLog);
        } catch (Exception e) {
            // 감사 저장 실패가 비즈니스 흐름을 깨뜨리지는 않되, 운영 로그에는 반드시 남긴다.
            log.error("[POINT_AUDIT] 감사 로그 저장 실패 — action={}, result={}",
                auditLog.getAction(), auditLog.getResult(), e);
        }
    }

}
