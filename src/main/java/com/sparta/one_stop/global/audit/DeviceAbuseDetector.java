package com.sparta.one_stop.global.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * deviceId 영역 의심 행위 탐지기
 *
 * <p><b>탐지 방식</b>: 상위 N건 조회 후 메모리 필터링이 아니라,
 * DB에서 직접 COUNT 집계한다. 트래픽이 많아도 컷오프 밖으로 밀려
 * 거짓 음성(false negative)이 생기지 않는다.
 *
 * <p>새 기기 판정은 metadata에 {@code "newDevice":true} 마커 포함 여부로,
 * Repository의 LIKE 조건으로 DB에서 직접 필터링한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceAbuseDetector {

    // 임계값 — 운영하며 조정
    private static final int NEW_DEVICE_THRESHOLD_PER_ACCOUNT = 5;   // 5분 5회
    private static final int NEW_DEVICE_THRESHOLD_PER_IP = 10;       // 10분 10계정
    private static final int EVICTION_ALERT_THRESHOLD = 50;          // 30분 50회

    /** metadata 내 새 기기 마커 — DeviceAbuse 기록 시 동일 포맷 사용 */
    private static final String NEW_DEVICE_MARKER = "\"newDevice\":true";

    private final SecurityAuditLogRepository auditRepository;
    private final SecurityAuditService auditService;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  실시간 탐지 — 로그인 직후 호출
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 다중 기기 폭주 탐지 — 같은 계정이 짧은 시간에 다발 새 기기 등록
     *
     * @return true면 의심 행위로 판정
     */
    public boolean detectMultiDeviceAbuse(Long userId) {
        if (userId == null) return false;

        LocalDateTime since = LocalDateTime.now().minusMinutes(5);
        // DB 직접 집계 (메모리 필터링 X → 누락 없음)
        long count = auditRepository.countRecentNewDeviceLoginsByUser(userId, since, NEW_DEVICE_MARKER);

        if (count >= NEW_DEVICE_THRESHOLD_PER_ACCOUNT) {
            log.warn("[DEVICE_ABUSE] 다중 기기 폭주 감지! userId={}, count={}/5분", userId, count);

            auditService.record(SecurityAuditEvent.builder()
                .eventType(SecurityAuditEventType.SUSPICIOUS_PATTERN_DETECTED)
                .actorUserId(userId)
                .result("DETECTED")
                .errorMessage(String.format("Multi-device abuse — %d new device logins in 5min", count))
                .metadata(String.format(
                    "{\"pattern\":\"MULTI_DEVICE_ABUSE\",\"userId\":%d,\"count\":%d}", userId, count))
                .build());

            // TODO: 호출자가 추가 조치 — 모든 기기 강제 로그아웃 + 재인증 요구
            return true;
        }
        return false;
    }

    /**
     * 분산 봇 공격 탐지 — 한 IP에서 여러 계정의 새 기기 등록 폭주
     */
    public boolean detectDistributedBotPattern(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return false;

        LocalDateTime since = LocalDateTime.now().minusMinutes(10);
        // COUNT(DISTINCT actorUserId)를 DB에서 직접 집계
        long distinctAccounts =
            auditRepository.countDistinctNewDeviceAccountsByIp(clientIp, since, NEW_DEVICE_MARKER);

        if (distinctAccounts >= NEW_DEVICE_THRESHOLD_PER_IP) {
            log.warn("[DEVICE_ABUSE] 분산 봇 공격 의심! ip={}, distinct accounts={}/10분",
                clientIp, distinctAccounts);

            auditService.record(SecurityAuditEvent.builder()
                .eventType(SecurityAuditEventType.SUSPICIOUS_PATTERN_DETECTED)
                .result("DETECTED")
                .errorMessage(String.format(
                    "Distributed bot suspected — %d distinct accounts from %s in 10min",
                    distinctAccounts, clientIp))
                .metadata(String.format(
                    "{\"pattern\":\"DISTRIBUTED_BOT\",\"ip\":\"%s\",\"accounts\":%d}",
                    clientIp, distinctAccounts))
                .build());

            // TODO: Redis 블랙리스트에 IP 자동 등록
            return true;
        }
        return false;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  주기 탐지 — 분산 패턴 정기 분석
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 5분마다 전체 시스템 의심 패턴 분석 — 실시간 탐지가 놓치는 누적 패턴
     */
    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void runPeriodicAnalysis() {
        try {
            LocalDateTime since = LocalDateTime.now().minusMinutes(30);
            long evictionCount = auditRepository.countRecentEvictions(since);

            if (evictionCount > EVICTION_ALERT_THRESHOLD) {
                log.warn("[DEVICE_ABUSE_PERIODIC] 비정상 추방 빈도! count={}/30분", evictionCount);
                // 시스템 전반 LRU 추방 폭주 → 공격 또는 버그 가능성
            }
        } catch (Exception e) {
            log.error("[DEVICE_ABUSE_PERIODIC] 주기 분석 실패", e);
        }
    }
}
