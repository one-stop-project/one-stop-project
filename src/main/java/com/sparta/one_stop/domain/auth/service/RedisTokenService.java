package com.sparta.one_stop.domain.auth.service;

import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.sparta.one_stop.global.common.RedisKeyConstants.BLACKLIST_PREFIX;
import static com.sparta.one_stop.global.common.RedisKeyConstants.REFRESH_TOKEN_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private final DeviceLimitService deviceLimitService;

    /** Lua Script — CAS 기반 RTR 원자적 갱신 */
    private static final String ROTATE_RT_SCRIPT =
        "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "   redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3]) " +
            "   return 1 " +
            "else " +
            "   return 0 " +
            "end";

    private static final RedisScript<Long> ROTATE_RT =
        new DefaultRedisScript<>(ROTATE_RT_SCRIPT, Long.class);


    // ═══════════════════════════════════════════════════════════
    //  Key 생성
    // ═══════════════════════════════════════════════════════════

    private String rtKey(Long userId, String deviceId) {
        return REFRESH_TOKEN_PREFIX + userId + ":" + deviceId;
    }

    private String blKey(String jti) {
        return BLACKLIST_PREFIX + jti;
    }


    // ═══════════════════════════════════════════════════════════
    //  Refresh Token 관리
    // ═══════════════════════════════════════════════════════════

    /**
     * RT 저장 — Fail-Close (보안 우선)
     *
     * Redis 장애 시 예외 발생 → 로그인 자체를 실패시킴
     * 이유: RT 없으면 토큰 재발급 불가 → 보안상 안전한 상태
     */
    public void saveRefreshToken(Long userId, String deviceId, String token, long expirySeconds) {
        String key = rtKey(userId, deviceId);
        try {
            redisTemplate.opsForValue().set(key, token, expirySeconds, TimeUnit.SECONDS);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("Redis 통신 장애 (RT 저장 실패) - Key: {}", key, e);
            throw new CustomException(ErrorCode.COMMON_008);
        }
    }

    /**
     * RT 조회 — Fail-Close
     */
    public String getRefreshToken(Long userId, String deviceId) {
        String key = rtKey(userId, deviceId);
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("Redis 통신 장애 (RT 조회 실패) - Key: {}", key, e);
            throw new CustomException(ErrorCode.COMMON_008);
        }
    }

    /**
     * RT 원자적 갱신 (Compare-And-Swap)
     *
     * 동시성 보장:
     *   - 같은 deviceId로 동시 refresh 요청 시 1건만 성공
     *   - 탈취된 RT를 다른 기기에서 사용 시 즉시 실패
     */
    public boolean rotateRefreshTokenCAS(Long userId, String deviceId, String oldToken,
                                         String newToken, long expirySeconds) {
        String key = rtKey(userId, deviceId);
        try {
            Long result = redisTemplate.execute(
                ROTATE_RT,
                List.of(key),
                oldToken, newToken, String.valueOf(expirySeconds)
            );
            return result != null && result == 1L;
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("Redis 통신 장애 (RTR Lua Script 실패) - Key: {}", key, e);
            throw new CustomException(ErrorCode.COMMON_008);
        }
    }

    /**
     * 특정 기기 RT 삭제 (로그아웃)
     */
    public void deleteRefreshToken(Long userId, String deviceId) {
        try {
            redisTemplate.delete(rtKey(userId, deviceId));
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("로그아웃 부분 실패 - RT 삭제 오류: userId={}, deviceId={}", userId, deviceId, e);
        }
    }

    /**
     * 사용자의 모든 기기 RT 일괄 삭제
     *
     * ⚠️ v8 대비 핵심 변경:
     *   - 기존: SCAN으로 RT:{userId}:* 패턴 검색
     *   - 신규: DeviceLimitService에 위임 (ZSET 인덱스 활용)
     *
     * @return 삭제된 RT 개수
     */
    public long deleteAllRefreshTokensByUserId(Long userId) {
        // DeviceLimitService가 ZSET 인덱스로 일괄 삭제 (SCAN 불필요)
        return deviceLimitService.removeAllDevices(userId);
    }


    // ═══════════════════════════════════════════════════════════
    //  Access Token 블랙리스트
    // ═══════════════════════════════════════════════════════════

    public void addToBlacklist(String jti, long expirySeconds) {
        try {
            redisTemplate.opsForValue().set(blKey(jti), "logout", expirySeconds, TimeUnit.SECONDS);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("로그아웃 부분 실패 - Blacklist 등록 오류: jti={}", jti, e);
        }
    }

    /**
     * 블랙리스트 등재 여부 확인
     *
     * [장애 정책: Fail-Open]
     * Redis 장애 시 전체 서비스 마비를 막기 위해 통과시킴
     * 보안 위험 < 가용성 우선
     */
    public boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(blKey(jti)));
        } catch (Exception e) {
            log.error("Redis 장애로 블랙리스트 검증 실패 (Fail-Open 동작): jti={}", jti, e);
            return false;
        }
    }
}
