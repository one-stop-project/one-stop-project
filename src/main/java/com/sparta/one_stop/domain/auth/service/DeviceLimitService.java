package com.sparta.one_stop.domain.auth.service;

import com.sparta.one_stop.global.common.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.sparta.one_stop.global.common.RedisKeyConstants.REFRESH_TOKEN_PREFIX;

/**
 *
 * 다중 기기 동시 로그인 제한 서비스
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
     *
     * 새 기기 등록 + 초과 시 가장 오래된 기기 제거
     *
     * 호출 시점: AuthService.login에서 RT 저장 직전
     *
     * @param userId   사용자 ID
     * @param deviceId 새 기기 식별자
     * @return 제거된 기기 ID (있으면), 없으면 null
     *
     */

    public String registerDevice(Long userId, String deviceId) {
        String key = DEVICES_KEY_PREFIX + userId;
        long now = System.currentTimeMillis();

        // 1. 새 기기 추가 (또는 기존 기기 timestamp 갱신)
        redisTemplate.opsForZSet().add(key, deviceId, now);
        redisTemplate.expire(key, DEVICES_TTL_SECONDS, TimeUnit.SECONDS);

        // 2. 기기 수 조회
        Long count = redisTemplate.opsForZSet().size(key);
        if (count == null || count <= MAX_DEVICES) {
            return null;  // 한도 내, 제거 불필요
        }

        // 3. 초과 — 가장 오래된 기기 추출 (score 낮은 순 = 가장 오래된)
        Set<String> oldest = redisTemplate.opsForZSet().range(key, 0, 0);
        if (oldest == null || oldest.isEmpty()) {
            return null;
        }

        String evictedDeviceId = oldest.iterator().next();

        // 4. ZSET에서 제거
        redisTemplate.opsForZSet().remove(key, evictedDeviceId);

        // 5. 해당 기기의 RT 삭제 (강제 로그아웃)
        String rtKey = REFRESH_TOKEN_PREFIX + userId + ":" + evictedDeviceId;
        redisTemplate.delete(rtKey);

        log.warn("[기기 제한] LRU 강제 로그아웃 — userId={}, 제거된 기기={}, 새 기기={}",
            userId, evictedDeviceId, deviceId);

        return evictedDeviceId;
    }

    /**
     * 기기 활동 시간 갱신 (LRU 갱신)
     *
     * 호출 시점: 토큰 재발급 (refresh) 시
     */
    public void touchDevice(Long userId, String deviceId) {
        String key = DEVICES_KEY_PREFIX + userId;
        redisTemplate.opsForZSet().add(key, deviceId, System.currentTimeMillis());
    }

    /**
     * 특정 기기 제거 (로그아웃)
     */
    public void removeDevice(Long userId, String deviceId) {
        String key = DEVICES_KEY_PREFIX + userId;
        redisTemplate.opsForZSet().remove(key, deviceId);
    }

    /**
     * 사용자의 모든 기기 제거 (탈퇴, 비밀번호 변경 등)
     */
    public long removeAllDevices(Long userId) {
        String zsetKey = DEVICES_KEY_PREFIX + userId;

        try {
            // 1. ZSET에 등록된 모든 기기 ID(deviceId)를 가져옵니다. (시간 복잡도 O(N), 최대 5대라 매우 빠름)
            Set<String> deviceIds = redisTemplate.opsForZSet().range(zsetKey, 0, -1);

            if (deviceIds == null || deviceIds.isEmpty()) {
                return 0L; // 지울 기기가 없으면 0 반환
            }

            // 2. 삭제할 키들을 한 바구니에 담습니다. (실제 RT 키들 + ZSET 키)
            Set<String> keysToDelete = new HashSet<>();
            for (String deviceId : deviceIds) {
                // 주의: RT Prefix 상수는 프로젝트 환경에 맞게 임포트해서 쓰세요!
                keysToDelete.add(REFRESH_TOKEN_PREFIX + userId + ":" + deviceId);
            }
            keysToDelete.add(zsetKey); // 마지막으로 ZSET(기기 목록) 자체도 지울 목록에 추가

            // 3. 한 번의 네트워크 통신으로 깔끔하게 일괄 삭제!
            Long deletedCount = redisTemplate.delete(keysToDelete);

            // ZSET 키 1개를 제외한 순수 RT 삭제 개수를 반환하거나, 전체 삭제 개수 반환
            return deletedCount != null ? deletedCount : 0L;

        } catch (Exception e) {
            log.error("[기기 제한] 전체 기기 로그아웃 실패 (Fail-Open): userId={}", userId, e);
            return 0L; // Redis 장애 시 회원탈퇴 로직 등이 멈추지 않도록 Fail-Open 처리
        }
    }

    /**
     * 사용자의 활성 기기 목록 조회
     *
     * @return ZSetTuple — {deviceId, lastActiveTimestamp}
     */
    public Set<ZSetOperations.TypedTuple<String>> listDevices(Long userId) {
        String key = DEVICES_KEY_PREFIX + userId;
        return redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
    }

    /**
     * 사용자의 활성 기기 수
     */
    public long countDevices(Long userId) {
        String key = DEVICES_KEY_PREFIX + userId;
        Long count = redisTemplate.opsForZSet().size(key);
        return count != null ? count : 0;
    }
}
