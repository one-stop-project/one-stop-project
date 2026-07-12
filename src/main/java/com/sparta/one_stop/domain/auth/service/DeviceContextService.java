package com.sparta.one_stop.domain.auth.service;

import com.sparta.one_stop.domain.auth.support.DeviceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceContextService {

    private static final String CTX_KEY_PREFIX = "devicectx:";
    private static final long CTX_TTL_SECONDS = Duration.ofDays(14).toSeconds();
    private final RedisTemplate<String, String> redisTemplate;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  쓰기 — 컨텍스트 등록/갱신 (login 및 검증통과 후 호출)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 컨텍스트 해시 등록/갱신 (upsert)
     *
     * 호출 시점:
     *
     *   로그인 성공 시 — 최초 컨텍스트 각인 (신뢰 기준점)
     *   refresh 검증 통과 후 — 정상 사용자의 환경 변화(OS 업데이트 등) 반영
     *
     *
     * 이전의 saveContext + refreshContext를 하나로 통합 (동작이 동일했음).
     */
    public void bindContext(Long userId, String deviceId, String userAgent, String clientIp) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return;
        }
        String hash = DeviceContext.hash(deviceId, userAgent, clientIp);
        try {
            redisTemplate.opsForValue().set(
                key(userId, deviceId), hash, CTX_TTL_SECONDS, TimeUnit.SECONDS
            );
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("[DEVICE_CTX] bindContext 실패 (Fail-Open): userId={}", userId, e);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  읽기 — 컨텍스트 검증 (refresh 시, 부수효과 없음)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 컨텍스트 일치 여부 검증 — 읽기 전용 (상태 변경 없음)
     *
     * 반환 의미
     *
     *   {@code MATCH}   — 저장된 컨텍스트와 일치
     *   {@code MISMATCH}— 불일치 (OS/브라우저/IP대역 변동) → 보안 이벤트 권장
     *   {@code UNKNOWN} — 저장된 컨텍스트 없음(만료/최초) 또는 Redis 장애
     *                         → 차단 신호로 쓰기엔 약함. 호출자가 정책 결정
     *
     */
    public ContextVerifyResult verifyContext(
        Long userId, String deviceId, String userAgent, String clientIp
    ) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return ContextVerifyResult.UNKNOWN;
        }

        String currentHash = DeviceContext.hash(deviceId, userAgent, clientIp);

        try {
            String storedHash = redisTemplate.opsForValue().get(key(userId, deviceId));

            if (storedHash == null) {
                // 최초이거나 만료 — 검증 기준이 없음. 부수효과 없이 UNKNOWN 반환.
                return ContextVerifyResult.UNKNOWN;
            }

            if (DeviceContext.matches(storedHash, currentHash)) {
                return ContextVerifyResult.MATCH;
            }

            log.warn("[DEVICE_CTX] 컨텍스트 불일치: userId={}, os={}, browser={}",
                userId,
                DeviceContext.parseOsType(userAgent),
                DeviceContext.parseBrowserType(userAgent));
            return ContextVerifyResult.MISMATCH;

        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("[DEVICE_CTX] verifyContext 실패 (Fail-Open: UNKNOWN): userId={}", userId, e);
            return ContextVerifyResult.UNKNOWN;
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  삭제 — 로그아웃/기기 제거 시
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public void removeContext(Long userId, String deviceId) {
        if (userId == null || deviceId == null || deviceId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(key(userId, deviceId));
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            log.warn("[DEVICE_CTX] removeContext 실패: userId={}", userId, e);
        }
    }

    private String key(Long userId, String deviceId) {
        return CTX_KEY_PREFIX + userId + ":" + deviceId;
    }

    /**
     * 컨텍스트 검증 결과
     */
    public enum ContextVerifyResult {
        /** 저장된 컨텍스트와 일치 */
        MATCH,
        /** 불일치 — 접속 환경 변동 (보안 이벤트/알림 트리거 권장) */
        MISMATCH,
        /** 검증 불가 — 저장된 컨텍스트 없음 또는 Redis 장애 (호출자가 정책 결정) */
        UNKNOWN
    }
}
