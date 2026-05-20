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

    // Key 생성 로직 통합 및 다중 기기(Device Fingerprint) 지원
    private String rtKey(Long userId, String deviceId) {
        return REFRESH_TOKEN_PREFIX + userId + ":" + deviceId;
    }

    private String blKey(String jti) {
        return BLACKLIST_PREFIX + jti;
    }

    // ── Refresh Token 관리 ──
    public void saveRefreshToken(Long userId, String deviceId, String token, long expirySeconds) {
        String key = rtKey(userId, deviceId);
        try {
            redisTemplate.opsForValue().set(key, token, expirySeconds, TimeUnit.SECONDS);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("Redis 통신 장애 (RT 저장 실패) - Key: {}", key, e);
            throw new CustomException(ErrorCode.COMMON_008);
        }
    }

    public String getRefreshToken(Long userId, String deviceId) {
        String key = rtKey(userId, deviceId);
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("Redis 통신 장애 (RT 조회 실패) - Key: {}", key, e);
            throw new CustomException(ErrorCode.COMMON_008);
        }
    }

    // Lua Script를 활용한 원자적 갱신 (Compare-And-Swap) - RTR 동시성 완벽 방어
    public boolean rotateRefreshTokenCAS(Long userId, String deviceId, String oldToken, String newToken, long expirySeconds) {
        String key = rtKey(userId, deviceId);
        String script =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                "   redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3]) " +
                "   return 1 " +
                "else " +
                "   return 0 " +
                "end";

        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);

        try {
            Long result = redisTemplate.execute(
                redisScript,
                List.of(key),
                oldToken, newToken, String.valueOf(expirySeconds)
            );
            return result != null && result == 1L;
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("Redis 통신 장애 (RTR Lua Script 실패) - Key: {}", key, e);
            throw new CustomException(ErrorCode.COMMON_008);
        }
    }

    // 특정 기기의 RT만 삭제(로그아웃)
    public void deleteRefreshToken(Long userId, String deviceId) {
        try {
            redisTemplate.delete(rtKey(userId, deviceId));
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("로그아웃 부분 실패 - RT 삭제 오류: userId={}, deviceId={}", userId, deviceId, e);
        }
    }

    // 특정 사용자의 모든 기기 RT 일괄 삭제
    // 호출 시점 : 1. 비밀번호 변경 시(보안 정책: 모든 기기 강제 로그아웃), 2. 회원 탈퇴 시 3. 관리자 강제 정지 시(필요 시)
    // 구현 방식 : SCAN 명령어 사용
    //      1. KEYS 명령어는 O(N)으로 Redis 전체를 블로킹하여 운영 환경에서 위험
    //      2. SCAN은 커서 기반 점진적 탐색 -> 운영 안정
    // 키 패턴 : RT:{userId}: * {모든 deviceId 매칭}

    public void deletedAllRefreshTokenByUserId(Long userId) {
        String pattern = REFRESH_TOKEN_PREFIX + userId + ":*";
        Set<String> keysToDelete = new HashSet<>();

        try {
            ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(100)
                .build();

            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                try (var cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        keysToDelete.add(new String(cursor.next()));
                    }
                }
                return null;
            });

            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                log.info("사용자 전체 기기 RT 삭제 완료 : userId={}, deletedCount={}",
                    userId, keysToDelete.size());

            }
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("Redis 통신 장애 (사용자 전체 RT 삭제 실패): userId={}", userId, e);
            // [정책] Fail-Open : 회원탈퇴, 비번변경 트랜잭션이 Redis 장애로 정지되지 않도록 하기 위함
            // 만료된 RT는 14일 후 자동정리 -> 보안 영향 미미
        }

    }

    // 예외 범위 축소 2
    public void addToBlacklist(String jti, long expirySeconds) {
        try {
            redisTemplate.opsForValue().set(blKey(jti), "logout", expirySeconds, TimeUnit.SECONDS);
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.error("로그아웃 부분 실패 - Blacklist 등록 오류: jti={}", jti, e);
        }
    }

    /**
     * 블랙리스트 등재 여부 확인
     * [장애 정책: Fail-Open] Redis 장애 시 전체 서비스 마비를 막기 위해 블랙리스트 검사를 통과시킨다.
     */
    public boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
        } catch (Exception e) {
            // 정책 결정 명문화
            log.error("Redis 장애로 블랙리스트 검증 실패 (Fail-Open 동작): jti={}", jti, e);
            return false; // Redis가 죽으면 차단하지 않고 그냥 통과시킴 (서비스 가용성 우선)

        }
    }

}
