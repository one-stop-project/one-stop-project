package com.sparta.one_stop.global.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 의심 행위 자동 탐지
 *
 * 탐지 룰
 *
 *   무차별 대입(Brute Force) — 같은 IP에서 5분 내 10회 이상 로그인 실패
 *   권한 우회 시도 — 같은 사용자가 5분 내 5회 이상 권한 위반
 *   CRITICAL 이벤트 — 1건이라도 발생 시 즉시 알림
 *
 *
 * 운영 시 확장
 *
 *   이상 패턴을 ML 모델로 학습 → 더 정교한 탐지
 *   Slack/PagerDuty 연동 → 즉시 알림
 *   자동 차단 — 의심 IP를 Redis 블랙리스트에 추가
 *
 *
 * 왜 스케줄러 + 즉시 탐지 둘 다?
 *
 *   실시간 탐지({@code detectOnRecord}) — CRITICAL 이벤트는 즉시 알림
 *   주기 스케줄({@code runPeriodicDetection}) — 패턴 분석은 일정 시간 누적 후
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuspiciousActivityDetector {

    /** 무차별 대입 임계값 — 5분 내 N회 이상 실패 */
    private static final int BRUTE_FORCE_THRESHOLD = 10;
    private static final int AUTHZ_VIOLATION_THRESHOLD = 5;
    private final SecurityAuditLogRepository repository;
    private final SecurityAuditService auditService;

    /**
     * 주기적 탐지 — 1분마다 최근 5분 데이터 분석
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)  // 1분 주기
    public void runPeriodicDetection() {
        try {
            LocalDateTime since = LocalDateTime.now().minusMinutes(5);

            // 1) CRITICAL 이벤트 — 즉시 알림 대상
            List<SecurityAuditLog> criticals = repository.findCriticalSince(since, PageRequest.of(0, 100)).getContent();
            if (!criticals.isEmpty()) {
                log.warn("[DETECTOR] CRITICAL 이벤트 {}건 감지", criticals.size());
                for (SecurityAuditLog c : criticals) {
                    notifyCritical(c);
                }
            }

            // 2) 추가 탐지 룰은 여기에 (스케줄러는 단순하게)
            // 실시간 탐지는 별도 메서드 활용 권장
        } catch (Exception e) {
            log.error("[DETECTOR] 주기 탐지 실패", e);
        }
    }

    /**
     * 로그인 실패 시 즉시 호출 — 무차별 대입 탐지
     *
     * 로그인 실패 이벤트가 기록된 직후 호출하면 IP 단위로 임계값 검사
     *
     * @param clientIp 클라이언트 IP
     * @return true면 의심 행위로 판정
     */
    public boolean detectBruteForce(String clientIp) {
        if (clientIp == null) return false;

        LocalDateTime since = LocalDateTime.now().minusMinutes(5);
        long failureCount = repository.countRecentLoginFailuresByIp(clientIp, since);

        if (failureCount >= BRUTE_FORCE_THRESHOLD) {
            log.warn("[DETECTOR] 무차별 대입 의심 감지! ip={}, failures={}/{}분",
                    clientIp, failureCount, 5);

            // 의심 행위로 별도 기록
            auditService.record(SecurityAuditEvent.builder()
                    .eventType(SecurityAuditEventType.SUSPICIOUS_PATTERN_DETECTED)
                    .result("DETECTED")
                    .errorMessage(String.format(
                            "Brute force suspected — %d login failures from %s in 5min",
                            failureCount, clientIp))
                    .metadata(String.format("{\"pattern\":\"BRUTE_FORCE\",\"ip\":\"%s\",\"count\":%d}",
                            clientIp, failureCount))
                    .build());

            // TODO: Redis IP 블랙리스트 자동 등록
            return true;
        }
        return false;
    }

    /**
     * 권한 위반 시 즉시 호출 — 권한 우회 시도 탐지
     */
    public boolean detectAuthzAbuse(Long userId) {
        if (userId == null) return false;

        LocalDateTime since = LocalDateTime.now().minusMinutes(5);
        long violationCount = repository.countRecentAuthzViolationsByUser(userId, since);

        if (violationCount >= AUTHZ_VIOLATION_THRESHOLD) {
            log.warn("[DETECTOR] 권한 우회 시도 감지! userId={}, violations={}/5분",
                    userId, violationCount);

            auditService.record(SecurityAuditEvent.builder()
                    .eventType(SecurityAuditEventType.SUSPICIOUS_PATTERN_DETECTED)
                    .actorUserId(userId)
                    .result("DETECTED")
                    .errorMessage(String.format(
                            "Authorization abuse suspected — %d violations from user %d in 5min",
                            violationCount, userId))
                    .metadata(String.format("{\"pattern\":\"AUTHZ_ABUSE\",\"userId\":%d,\"count\":%d}",
                            userId, violationCount))
                    .build());
            return true;
        }
        return false;
    }

    private void notifyCritical(SecurityAuditLog log) {
        // TODO: Slack/PagerDuty 연동
        SuspiciousActivityDetector.log.error(
                "[CRITICAL_ALERT] {} | user={} | ip={} | target={}/{} | msg={}",
                log.getEventType(),
                log.getActorUserId(),
                log.getClientIp(),
                log.getTargetResource(),
                log.getTargetId(),
                log.getErrorMessage());
    }
}
