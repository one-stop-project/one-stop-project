package com.sparta.one_stop.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.sparta.one_stop.global.common.RedisKeyConstants.REFRESH_TOKEN_PREFIX;

/**
 *
 * 다중 기기 동시 로그인 제한 서비스 — 동시성 안전판
 *
 * 정책:
 *   - 한 사용자당 최대 5개 기기 동시 로그인 (MAX_DEVICES)
 *   - 초과 시 가장 오래된 기기 자동 로그아웃 (LRU)
 *   - 기기 수 = PC + 모바일 + 태블릿 + 회사PC + 예비 = 5개 적정
 * 데이터 구조 (Redis):
 *   1. ZSET: 기기별 최근 활동 시간 — devices:{userId} → {deviceId: timestamp}
 *   2. STRING: RT 저장 — RT:{userId}:{deviceId} → token
 * ZSET 사용 이유:
 *   - score = 마지막 활동 timestamp
 *   - ZRANGE로 오래된 순 정렬
 *   - ZREMRANGEBYRANK로 일괄 제거
 * 보안 효과:
 *   - 통계적으로 5개 초과는 사용자가 아닌 공격자 가능성 높음
 *   - RT 탈취 후 무한 기기 등록 차단
 *   - 새 기기 로그인 시 가장 오래된 기기 강제 로그아웃
 *
 *
 *
 *   ---------------- 기능 설명 -----------------------
 *  1. registerDevice — Lua Script 원자성
 *     - 단일 Lua Script = 1회 원자 실행
 *     → 동시 로그인 시 MAX_DEVICES 초과 우회 차단
 *
 *  2. touchDevice — TTL 갱신 추가
 *     - TTL도 함께 갱신 → removeAllDevices에서 RT 누락 방지
 *
 *  3. 입력 검증 강화
 *     - deviceId null/blank 차단
 *     - userId null 차단
 *
 *  4. Fail-Open 정책 명문화
 *     - Redis 장애 시 가용성 우선
 *     - 단, 보안 로그 반드시 남김
 *
 *  ---------------- 데이터 구조 -----------------------
 *  ZSET: devices:{userId}
 *    - member: deviceId
 *    - score:  마지막 활동 timestamp (밀리초)
 *
 *  STRING: RT:{userId}:{deviceId}
 *    - value: refresh token JWT
 *    - TTL:   14일 (DEVICES_TTL_SECONDS와 동일)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceLimitService {

    private final RedisTemplate<String, String> redisTemplate;
    /** 사용자당 최대 동시 로그인 기기 수 */
    public static final int MAX_DEVICES = 5;
    /** ZSET 키 prefix */
    private static final String DEVICES_KEY_PREFIX = "devices:";
    /** 기기 활동 ZSET TTL (RT와 동일 기간) */
    private static final long DEVICES_TTL_SECONDS = 14 * 24 * 60 * 60;  // 14일

    /**
     * registerDevice Lua Script — 원자적 기기 등록 + LRU 추방
     *
     * KEYS[1] = devices:{userId}      (ZSET 인덱스)
     * KEYS[2] = RT:{userId}:          (RT 키 prefix)
     *
     * ARGV[1] = newDeviceId
     * ARGV[2] = timestamp (now)
     * ARGV[3] = MAX_DEVICES
     * ARGV[4] = TTL seconds
     *
     * 흐름:
     *   1. ZADD: 새 기기 추가 (기존이면 timestamp 갱신)
     *   2. EXPIRE: ZSET TTL 갱신
     *   3. ZCARD: 기기 수 확인
     *   4. MAX_DEVICES 초과 시:
     *      a. ZRANGE 0 0: 가장 오래된 기기 추출
     *      b. ZREM: ZSET에서 제거
     *      c. DEL: 해당 기기 RT 삭제
     *      d. 반환: 추방된 deviceId
     *   5. 한도 내: 빈 문자열 반환
     *
     * 원자성: 위 모든 작업이 단일 트랜잭션처럼 실행
     */
    private static final String REGISTER_DEVICE_SCRIPT =
        "redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1]) " +
            "redis.call('EXPIRE', KEYS[1], ARGV[4]) " +
            "local count = redis.call('ZCARD', KEYS[1]) " +
            "if count > tonumber(ARGV[3]) then " +
            "local oldest = redis.call('ZRANGE', KEYS[1], 0, 0) " +
            " if #oldest > 0 then " +
            " redis.call('ZREM', KEYS[1], oldest[1]) " +
            " redis.call('DEL', KEYS[2] .. oldest[1]) " +
            "return oldest[1]" +
            "end" +
            "end" +
            "return ''";

    private static final RedisScript<String> REGISTER_DEVICE =
        new DefaultRedisScript<>(REGISTER_DEVICE_SCRIPT, String.class);

    private static final String REMOVE_ALL_DEVICES_SCRIPT =
        "local devices = redis.call('ZRANGE', KEYS[1], 0, -1) " +
            "local count = #devices" +
            "for i = 1, count do" +
            "redis.call('DEL', KEYS[2] .. devices[i]) " +
            "end" +
            "redis.call('DEL', KEYS[1]) " +
            "return count";

    private static final RedisScript<Long> REMOVE_ALL_DEVICES =
        new DefaultRedisScript<>(REMOVE_ALL_DEVICES_SCRIPT, Long.class);

    // 새 기기 등록 + 초과 시 가장 오래된 기기 자동 로그아웃(원자적)
    // @return 추방된 deviceId, 한도 내였으면 null

    public String registerDevice(Long userId, String deviceId) {
        validateInputs(userId, deviceId);

        String zsetKey = devicesKey(userId);
        String rtPrefix = REFRESH_TOKEN_PREFIX + userId + ":";
        long now = System.currentTimeMillis();

        try {
            String evicted = redisTemplate.execute(
                REGISTER_DEVICE,
                List.of(zsetKey, rtPrefix),
                deviceId,
                String.valueOf(now),
                String.valueOf(MAX_DEVICES),
                String.valueOf(DEVICES_TTL_SECONDS)
            );

            if (evicted != null && !evicted.isEmpty()) {
                log.warn("[기기 제한] LRU 강제 로그아웃 - userId={}, evicted={}, new={}",
                    userId, evicted, deviceId);
                return evicted;
            }
            return null;
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            // Fail-Open : Redis 장애 시 로그인은 허용
            log.error("[기기제한] Redis 장애 - Fail-Open : userId={}", userId, e);
            return null;
        }
    }

    /**
     * 기기 활동 시간 갱신 (LRU 갱신)
     *
     * 호출 시점: 토큰 재발급 (refresh) 시
     * TTL도 함께 갱신
     * refresh가 일어나도 ZSET이 RT보다 먼저 만료되면
     * removeAllDevices에서 일부 RT 누락됨
     */
    public void touchDevice(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return;
        }

        String key = devicesKey(userId);
        try {
            redisTemplate.opsForZSet().add(key, deviceId, System.currentTimeMillis());
            redisTemplate.expire(key, DEVICES_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("[기기 제한] touchDevice 실패 (무시) : userId={}, deviceId ={}", userId, deviceId);
        }

    }

    /**
     * 특정 기기 제거 (로그아웃)
     */
    public void removeDevice(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) return;

        try {
            redisTemplate.opsForZSet().remove(devicesKey(userId), deviceId);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("[기기 제한] removeDevice 실패: userId={}, deviceId={}", userId, deviceId);
        }
    }

    /**
     * 사용자의 모든 기기 제거 + 모든 RT 삭제 (Lua Script 원자 실행)
     *
     * 호출 시점:
     *   - 비밀번호 변경 시
     *   - 회원 탈퇴 시
     *   - 관리자 강제 정지 시
     *
     * 특징 :
     *   - 다중 명령어 → Lua Script로 원자성 보장
     *   - SCAN 없이 ZSET 인덱스만으로 처리 (1000배 빠름)
     *
     * @return 삭제된 기기 수
     */
    public long removeAllDevices(Long userId) {
        if (userId == null) return 0L;

        String zsetKey = devicesKey(userId);
        String rtPrefix = REFRESH_TOKEN_PREFIX + userId + ":";

        try {
            Long deletedCount = redisTemplate.execute(
                REMOVE_ALL_DEVICES,
                List.of(zsetKey, rtPrefix)
            );

            long count = deletedCount != null ? deletedCount : 0L;
            if (count > 0) {
                log.info("[기기 제한] 전체 기기 로그아웃: userId={}, count={}", userId, count);
            }
            return count;

        } catch (RedisConnectionFailureException | RedisSystemException e) {
            // Fail-Open: 비밀번호 변경/탈퇴 막지 않도록
            log.error("[기기 제한] removeAllDevices 실패 (Fail-Open): userId={}", userId, e);
            return 0L;
        }
    }

    /**
     * 사용자의 활성 기기 목록 조회
     *
     * @return ZSetTuple — {deviceId, lastActiveTimestamp}
     */
    public Set<ZSetOperations.TypedTuple<String>> listDevices(Long userId) {
        if (userId == null) return Set.of();

        try {
            return redisTemplate.opsForZSet().rangeWithScores(devicesKey(userId), 0, -1);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("[기기 제한] listDevices 실패: userId={}", userId, e);
            return Set.of();
        }
    }

    /**
     * 사용자의 활성 기기 수
     */
    public long countDevices(Long userId) {
        if (userId == null) return 0L;

        try {
            Long count = redisTemplate.opsForZSet().size(devicesKey(userId));
            return count != null ? count : 0L;
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            return 0L;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Private
    // ═══════════════════════════════════════════════════════════

    private String devicesKey(Long userId) {
        return DEVICES_KEY_PREFIX + userId;
    }

    private void validateInputs(Long userId, String deviceId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId cannot be null or blank");
        }
        if (deviceId.length() > 100) {
            throw new IllegalArgumentException("deviceId too long (max 100 chars)");
        }
    }

}
