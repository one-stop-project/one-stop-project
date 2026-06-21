package com.sparta.one_stop.domain.auth.service;

import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.sparta.one_stop.global.common.RedisKeyConstants.BLACKLIST_PREFIX;
import static com.sparta.one_stop.global.common.RedisKeyConstants.DEVICE_ZSET_PREFIX;
import static com.sparta.one_stop.global.common.RedisKeyConstants.REFRESH_TOKEN_PREFIX;
import static com.sparta.one_stop.global.common.RedisKeyConstants.USER_TOKEN_CUTOFF_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenService {

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
    private static final String DELETE_ALL_USER_SESSIONS_SCRIPT =
        "local devices = redis.call('ZRANGE', KEYS[1], 0, -1) " +
            "local deleted = 0 " +
            "for _, deviceId in ipairs(devices) do " +
            "  deleted = deleted + redis.call('DEL', ARGV[1] .. deviceId) " +
            "end " +
            "redis.call('DEL', KEYS[1]) " +
            "return deleted";
    private static final RedisScript<Long> DELETE_ALL_USER_SESSIONS =
        new DefaultRedisScript<>(DELETE_ALL_USER_SESSIONS_SCRIPT, Long.class);
    private static final String CONSUME_OAUTH2_CODE_SCRIPT =
        "local value = redis.call('GET', KEYS[1]) " +
            "if not value then return nil end " +
            "local expectedPrefix = ARGV[1] .. ':' " +
            "if string.sub(value, 1, string.len(expectedPrefix)) ~= expectedPrefix then return nil end " +
            "redis.call('DEL', KEYS[1]) " +
            "return value";
    private static final RedisScript<String> CONSUME_OAUTH2_CODE =
        new DefaultRedisScript<>(CONSUME_OAUTH2_CODE_SCRIPT, String.class);
    // ═══════════ OAuth2 일회용 핸드오프 code ═══════════
    private static final String OAUTH2_CODE_PREFIX = "oauth2:code:"; // 일관성 위해 RedisKeyConstants로 옮겨도 됨
    private static final long OAUTH2_CODE_TTL_SECONDS = 60;


    // ═══════════════════════════════════════════════════════════
    //  Key 생성
    // ═══════════════════════════════════════════════════════════
    private static final String CODE_SEP = ":"; // JWT(base64url+'.')·UUID 모두 ':' 미포함 → 구분자 안전
    private final RedisTemplate<String, String> redisTemplate;


    // ═══════════════════════════════════════════════════════════
    //  Refresh Token 관리
    // ═══════════════════════════════════════════════════════════
    private String rtKey(Long userId, String deviceId) {
        return REFRESH_TOKEN_PREFIX + userId + ":" + deviceId;
    }

    private String blKey(String jti) {
        return BLACKLIST_PREFIX + jti;
    }

    /**
     * RT 저장 — Fail-Close (보안 우선)
     *
     * Redis 장애 시 예외 발생 → 로그인 자체를 실패시킴
     * 이유: RT 없으면 토큰 재발급 불가 → 보안상 안전한 상태
     */
    public void saveRefreshToken(Long userId, String deviceId, String token, long expirySeconds) {
        String key = rtKey(userId, deviceId);
        try {
            redisTemplate.opsForValue().set(key, hashToken(token), expirySeconds, TimeUnit.SECONDS);
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


    // ═══════════════════════════════════════════════════════════
    //  Access Token 블랙리스트
    // ═══════════════════════════════════════════════════════════

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
                hashToken(oldToken), hashToken(newToken), String.valueOf(expirySeconds)
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

    // ═══════════════════════════════════════════════════════════
    //  User-level 토큰 무효화 (iat-cutoff)
    //  비번변경/탈퇴/정지 시 호출 → 그 이전 발급된 모든 AT를 다음 요청에서 거부
    // ═══════════════════════════════════════════════════════════

    /**
     * 사용자의 모든 기기 RT 일괄 삭제
     *
     *   - 기존: SCAN으로 RT:{userId}:* 패턴 검색
     *   - 신규: DeviceLimitService에 위임 (ZSET 인덱스 활용)
     *
     * @return 삭제된 RT 개수
     */
    public long deleteAllRefreshTokensByUserId(Long userId) {
        try {
            Long deleted = redisTemplate.execute(
                DELETE_ALL_USER_SESSIONS,
                List.of(DEVICE_ZSET_PREFIX + userId),
                REFRESH_TOKEN_PREFIX + userId + ":"
            );
            return deleted == null ? 0L : deleted;
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("전체 Refresh Token 삭제 실패: userId={}", userId, e);
            throw new CustomException(ErrorCode.COMMON_008);
        }
    }

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

    /**
     * 사용자의 모든 기존 AT 무효화 — cutoff 시각을 현재로 기록
     *
     * <p>AT 수명(15분)만큼만 TTL 부여. 그보다 오래된 AT는 어차피 만료되므로
     * cutoff도 만료시켜 Redis 용량을 자동 회수한다.
     *
     * @param accessTokenTtlSeconds AT 만료 시간(초) — JwtTokenProvider에서 전달
     */
    public void invalidateUserTokens(Long userId, long accessTokenTtlSeconds) {
        try {
            // 현재 epoch(초) 기록. 이 시각 이전 iat를 가진 AT는 모두 거부됨.
            String now = String.valueOf(System.currentTimeMillis() / 1000);
            redisTemplate.opsForValue().set(
                cutoffKey(userId), now, accessTokenTtlSeconds, TimeUnit.SECONDS);
            log.info("[토큰무효화] user-level cutoff 등록: userId={}, cutoff={}", userId, now);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("[토큰무효화] cutoff 등록 실패: userId={}", userId, e);
        }
    }

    /**
     * AT의 발급시각(iat)이 무효화 cutoff 이전인지 검사
     *
     * @param userId    토큰 소유자
     * @param issuedAtEpochSeconds AT의 iat (epoch 초)
     * @return true면 무효화된 토큰 (거부해야 함)
     *
     * <p>Fail-Open: Redis 장애 시 false(통과). 가용성 우선.
     */
    public boolean isTokenInvalidated(Long userId, long issuedAtEpochSeconds) {
        try {
            String cutoff = redisTemplate.opsForValue().get(cutoffKey(userId));
            if (cutoff == null) {
                return false; // 무효화 이력 없음
            }
            long cutoffEpoch = Long.parseLong(cutoff);
            // iat가 cutoff 이전(<=)이면 무효화 대상
            return issuedAtEpochSeconds <= cutoffEpoch;
        } catch (Exception e) {
            log.error("[토큰무효화] cutoff 검증 실패 (Fail-Open): userId={}", userId, e);
            return false;
        }
    }

    private String cutoffKey(Long userId) {
        return USER_TOKEN_CUTOFF_PREFIX + userId;
    }

    private String oauth2CodeKey(String code) {
        return OAUTH2_CODE_PREFIX + code;
    }

    /** code 저장 — Fail-Close. 저장 못 하면 교환 불가이므로 핸드오프를 실패시킨다. */
    public void saveOAuth2Code(String code, String deviceId, String accessToken) {
        try {
            redisTemplate.opsForValue().set(
                oauth2CodeKey(code), deviceId + CODE_SEP + accessToken,
                OAUTH2_CODE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("Redis 통신 장애 (OAuth2 code 저장 실패)", e);
            throw new CustomException(ErrorCode.COMMON_008);
        }
    }

    /** code와 device_id가 일치할 때만 원자적으로 1회 소비한다. */
    public OAuth2Handoff consumeOAuth2Code(String code, String expectedDeviceId) {
        String value;
        try {
            value = redisTemplate.execute(
                CONSUME_OAUTH2_CODE,
                List.of(oauth2CodeKey(code)),
                expectedDeviceId
            );
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("Redis 통신 장애 (OAuth2 code 소비 실패)", e);
            throw new CustomException(ErrorCode.COMMON_008);
        }
        if (value == null) return null;
        int idx = value.indexOf(CODE_SEP);
        if (idx < 0) return null; // 손상된 값
        return new OAuth2Handoff(value.substring(0, idx), value.substring(idx + 1));
    }

    /**
     * RT를 SHA-256으로 단방향 해싱 (Hex 문자열)
     *
     * <p>RT는 이미 HMAC 서명된 고엔트로피 JWT라 무차별 대입/레인보우 테이블
     * 위험이 없다. 따라서 비밀번호용 bcrypt(느린 해시)가 아니라 빠른 SHA-256으로
     * 충분하다 — 매 refresh마다 호출되므로 성능도 중요.
     *
     * <p>목적: Redis가 유출돼도 저장된 해시값으로는 재발급(refresh)이 불가능.
     * (JWT 서명 검증과는 별개의 방어선 — 서명은 "우리가 발급했나",
     *  해시 저장은 "저장소 유출 시 재사용 차단")
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JVM 표준 — 실질적으로 발생 불가
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다", e);
        }
    }

    public record OAuth2Handoff(String deviceId, String accessToken) {}

}
