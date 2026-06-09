package com.sparta.one_stop.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 다중 기기 세션 제한 서비스 — 강화본
 *
 *
 *   Fail-Open 정책 명문화 — Redis 장애 시 인증 차단되지 않도록
 *   {@link #isNewDevice(Long, String)} 추가 — 새 기기 감지 (보안 이벤트 트리거용)
 *   {@link DeviceRegistrationResult} 반환 — 단순 String 이상의 정보 제공
 *   {@link #HARD_DEVICE_LIMIT} — ZSET 폭주 방어 절대 상한 (방어선)
 *   Lua Script 재정의 — 의심 행위 정보까지 한 번에 반환
 *
 *
 * Redis ZSET 구조
 *
 *   Key:   devices:{userId}
 *   Score: timestamp (마지막 활동 시각)
 *   Value: deviceId
 *   TTL:   RT 만료 기간과 동일
 *
 *
 * Fail-Open 정책
 * Redis 장애 시 {@link RedisConnectionFailureException} 발생 시:
 *
 *   {@code registerDevice}: 로그인 허용 (인증은 통과, 기기 추적만 못함)
 *   {@code touchDevice}: 무시 (활동 시간 갱신 못해도 무방)
 *   {@code removeDevice}: 무시 (다음 만료 시 자연 정리)
 *   {@code countDevices}: 0 반환 (제한 검증 우회 방지)
 *
 * 이유: 인증은 시스템 진입점 → 가용성이 보안보다 우선되는 경우가 있음.
 * 대신 모든 Fail-Open 발생을 로그로 남기고, 보안 감사 로그에 기록.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceLimitService {

    /** 정상 상한 — 한 계정 5개 기기 LRU */
    public static final int MAX_DEVICES = 5;
    /**
     * 절대 상한 — ZSET이 비정상적으로 커지는 것 방어
     *
     * 정상 흐름에선 MAX_DEVICES=5를 넘지 않지만,
     * 버그/공격으로 ZSET이 무한 증가하는 사태를 막는 가드.
     * 이 값을 넘으면 강제 정리 + CRITICAL 보안 이벤트 발생.
     */
    public static final int HARD_DEVICE_LIMIT = 100;
    private static final String ZSET_KEY_PREFIX = "devices:";
    private static final Duration ZSET_TTL = Duration.ofDays(14);
    /** Lua Script — 원자적 등록 + 추방 + 새 기기 여부 반환 */
    private static final DefaultRedisScript<List> REGISTER_SCRIPT = createRegisterScript();
    private final RedisTemplate<String, String> redisTemplate;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  핵심 — 기기 등록
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Lua Script — 원자적 실행
     *   KEYS[1] — devices:{userId} ZSET 키
     *   ARGV[1] — deviceId
     *   ARGV[2] — 현재 timestamp
     *   ARGV[3] — MAX_DEVICES
     *   ARGV[4] — TTL (초)
     *
     *
     * 반환: {@code [isNewDevice(0/1), evictedDeviceId|nil, currentSize]}
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DefaultRedisScript<List> createRegisterScript() {
        String script = """
                local key = KEYS[1]
                local deviceId = ARGV[1]
                local timestamp = tonumber(ARGV[2])
                local maxDevices = tonumber(ARGV[3])
                local ttlSeconds = tonumber(ARGV[4])

                -- 1) 새 기기 여부 확인
                local existingScore = redis.call('ZSCORE', key, deviceId)
                local isNew = 0
                if existingScore == false then
                    isNew = 1
                end

                -- 2) 등록/갱신
                redis.call('ZADD', key, timestamp, deviceId)
                redis.call('EXPIRE', key, ttlSeconds)

                -- 3) MAX_DEVICES 초과 시 가장 오래된 것 추방
                local evicted = nil
                local size = redis.call('ZCARD', key)
                if size > maxDevices then
                    local oldest = redis.call('ZRANGE', key, 0, 0)
                    if oldest and #oldest > 0 then
                        evicted = oldest[1]
                        redis.call('ZREM', key, evicted)
                    end
                    size = size - 1
                end

                return {isNew, evicted, size}
                """;
        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(List.class);
        return redisScript;
    }

    /**
     * 기기 등록 + LRU 추방 (원자적 실행)
     *
     * 변경: 반환 타입이 {@code String}(추방된 deviceId) → {@link DeviceRegistrationResult}
     * 호출처가 "새 기기 여부", "추방 발생 여부", "현재 기기 수" 등을 알 수 있도록.
     *
     * @return 등록 결과 객체 (Fail-Open 시에도 null 아님)
     */
    public DeviceRegistrationResult registerDevice(Long userId, String deviceId) {
        String key = key(userId);

        try {
            List<Object> result = redisTemplate.execute(
                REGISTER_SCRIPT,
                Collections.singletonList(key),
                deviceId,
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(MAX_DEVICES),
                String.valueOf(ZSET_TTL.toSeconds())
            );

            // Lua 반환 형식: [isNewDevice, evictedDeviceId|nil, currentSize]
            boolean isNew = "1".equals(String.valueOf(result.get(0)));
            String evicted = result.get(1) != null ? String.valueOf(result.get(1)) : null;
            int currentSize = Integer.parseInt(String.valueOf(result.get(2)));

            // 절대 상한 초과 — 비정상 상태
            if (currentSize > HARD_DEVICE_LIMIT) {
                log.error("[DEVICE_LIMIT] 절대 상한 초과! userId={}, currentSize={}, HARD_LIMIT={}",
                    userId, currentSize, HARD_DEVICE_LIMIT);
                // 강제 정리 — 최근 MAX_DEVICES 개만 남기고 모두 추방
                forcePruneToMaxDevices(userId);
                // 보안 이벤트는 호출자(AuthService)에서 SecurityAuditService로 기록
            }

            return new DeviceRegistrationResult(isNew, evicted, currentSize, false);

        } catch (RedisConnectionFailureException e) {
            // ★ Fail-Open — 인증은 통과시키되 로그 남김
            log.error("[DEVICE_LIMIT] Redis 장애 — Fail-Open으로 로그인 허용 (userId={}, deviceId={})",
                userId, deviceId, e);
            return new DeviceRegistrationResult(true, null, 0, true);
        } catch (Exception e) {
            // 예상 못한 예외 — Fail-Open
            log.error("[DEVICE_LIMIT] 예상 못한 예외 — Fail-Open (userId={})", userId, e);
            return new DeviceRegistrationResult(true, null, 0, true);
        }
    }

    /**
     * 기기 활동 시각 갱신
     *
     * RT 갱신 시 호출 → LRU 순서에서 밀려나지 않도록.
     * Redis 장애 시 무시 (다음 활동 시 다시 갱신됨).
     */
    public void touchDevice(Long userId, String deviceId) {
        try {
            String key = key(userId);
            redisTemplate.opsForZSet().add(key, deviceId, System.currentTimeMillis());
            redisTemplate.expire(key, ZSET_TTL);
        } catch (RedisConnectionFailureException e) {
            log.warn("[DEVICE_LIMIT] touchDevice 실패 (Fail-Open): userId={}", userId, e);
        }
    }

    /**
     * 새 기기 여부 확인 — 보안 이벤트 트리거용
     *
     * 로그인 시도 시 ZSET에 이미 있는 deviceId인지 확인.
     * 없으면 새 기기 → 보안 이벤트 + (선택) 추가 인증 요구.
     *
     * 주의: 이 메서드는 등록 전에 호출. 등록 후엔 무조건 존재함.
     */
    public boolean isNewDevice(Long userId, String deviceId) {
        try {
            Double score = redisTemplate.opsForZSet().score(key(userId), deviceId);
            return score == null;
        } catch (RedisConnectionFailureException e) {
            // Fail-Open — 새 기기로 간주 (보수적으로 보안 이벤트 발생)
            log.warn("[DEVICE_LIMIT] isNewDevice 확인 실패 (Fail-Open): userId={}", userId, e);
            return true;
        }
    }

    public void removeDevice(Long userId, String deviceId) {
        try {
            redisTemplate.opsForZSet().remove(key(userId), deviceId);
        } catch (RedisConnectionFailureException e) {
            log.warn("[DEVICE_LIMIT] removeDevice 실패 (Fail-Open): userId={}", userId, e);
        }
    }

    public long removeAllDevices(Long userId) {
        try {
            Long size = redisTemplate.opsForZSet().size(key(userId));
            redisTemplate.delete(key(userId));
            return size != null ? size : 0;
        } catch (RedisConnectionFailureException e) {
            log.warn("[DEVICE_LIMIT] removeAllDevices 실패: userId={}", userId, e);
            return 0;
        }
    }

    public Set<ZSetOperations.TypedTuple<String>> listDevices(Long userId) {
        try {
            return redisTemplate.opsForZSet().reverseRangeWithScores(key(userId), 0, -1);
        } catch (RedisConnectionFailureException e) {
            log.warn("[DEVICE_LIMIT] listDevices 실패: userId={}", userId, e);
            return Collections.emptySet();
        }
    }

    public long countDevices(Long userId) {
        try {
            Long count = redisTemplate.opsForZSet().size(key(userId));
            return count != null ? count : 0;
        } catch (RedisConnectionFailureException e) {
            // ★ 카운트 실패 시 0 반환 — 제한 검증 우회 방지
            log.warn("[DEVICE_LIMIT] countDevices 실패 (Fail-Open: return 0): userId={}", userId, e);
            return 0;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  내부 — 절대 상한 초과 시 강제 정리
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private void forcePruneToMaxDevices(Long userId) {
        try {
            String key = key(userId);
            // 가장 오래된 것부터 (현재 수 - MAX_DEVICES) 개 제거
            redisTemplate.opsForZSet().removeRange(key, 0, -MAX_DEVICES - 1);
            log.warn("[DEVICE_LIMIT] 강제 정리 완료 (HARD_LIMIT 초과): userId={}, kept={}",
                userId, MAX_DEVICES);
        } catch (Exception e) {
            log.error("[DEVICE_LIMIT] 강제 정리 실패: userId={}", userId, e);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Lua Script — 원자적 등록 + 추방
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private String key(Long userId) {
        return ZSET_KEY_PREFIX + userId;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  결과 객체
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 기기 등록 결과
     *
     * @param isNewDevice    이 기기가 처음 등록되는가
     * @param evictedDeviceId LRU 추방된 기기 ID (없으면 null)
     * @param currentSize    등록 후 현재 기기 수
     * @param failOpen       Redis 장애로 Fail-Open 발생 여부
     */
    public record DeviceRegistrationResult(
        boolean isNewDevice,
        String evictedDeviceId,
        int currentSize,
        boolean failOpen
    ) {
    }
}
