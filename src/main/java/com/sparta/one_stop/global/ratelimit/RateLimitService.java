package com.sparta.one_stop.global.ratelimit;

import com.sparta.one_stop.global.enums.ratelimit.RateLimitPolicy;
import com.sparta.one_stop.global.audit.SecurityAuditEvent;
import com.sparta.one_stop.global.audit.SecurityAuditEventType;
import com.sparta.one_stop.global.audit.SecurityAuditService;
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

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    /**
     * Lua Script — 원자적 카운팅 + TTL 관리 + 대기 시간 반환
     *
     * 흐름:
     *   1. INCR로 카운트 증가
     *   2. 첫 호출(count=1)이면 EXPIRE 설정
     *   3. limit 초과 시 음수(-TTL) 반환 (대기 시간 안내)
     *   4. 정상 통과 시 양수(현재 count) 반환
     *
     * 반환값:
     *   - 양수: 현재 count (통과)
     *   - 음수: -남은 TTL 초 (차단)
     *   - 0:   에러 (발생 안 함)
     */
    private static final String RATE_LIMIT_SCRIPT =
        "local current = redis.call('INCR', KEYS[1]) " +
            "if tonumber(current) == 1 then " +
            "   redis.call('EXPIRE', KEYS[1], ARGV[1]) " +
            "end " +
            "if tonumber(current) > tonumber(ARGV[2]) then " +
            "   local ttl = redis.call('TTL', KEYS[1]) " +
            "   if ttl < 0 then ttl = tonumber(ARGV[1]) end " +
            "   return -ttl " +
            "end " +
            "return current";
    private static final RedisScript<Long> RATE_LIMIT =
        new DefaultRedisScript<>(RATE_LIMIT_SCRIPT, Long.class);
    private final RedisTemplate<String, String> redisTemplate;
    private final SecurityAuditService securityAuditService;

    /**
     * Rate Limit 검증 — 통과 시 정상, 차단 시 COMMON_009 예외
     *
     * @param policy     적용할 정책
     * @param identifier 식별자 (이메일, IP, userId 등)
     * @throws CustomException COMMON_009 (429 Too Many Requests)
     */
    public void tryConsume(RateLimitPolicy policy, String identifier) {
        String redisKey = policy.buildKey(identifier);

        try {
            Long result = redisTemplate.execute(
                RATE_LIMIT,
                List.of(redisKey),
                String.valueOf(policy.getWindowSeconds()),
                String.valueOf(policy.getLimit())
            );

            // 음수 = 차단 (절댓값 = 대기 시간 초)
            if (result != null && result < 0) {
                long waitSeconds = Math.abs(result);

                log.warn("[Rate Limit 차단] policy={}, id={}, 대기시간={}초, description={}",
                    policy.name(), maskIdentifier(identifier), waitSeconds, policy.getDescription());

                securityAuditService.record(SecurityAuditEvent.builder()
                    .eventType(SecurityAuditEventType.RATE_LIMIT_BLOCKED)
                    .result("BLOCKED")
                    .ruleCode("RATE_LIMIT_TRIGGERED")
                    .errorCode(ErrorCode.COMMON_009.getCode())
                    .errorMessage("Rate limit blocked: " + policy.name())
                    .clientIp(policy.name().contains("IP") ? identifier : null)
                    .suspicious(true)
                    .build());

                throw new CustomException(
                    ErrorCode.COMMON_009,
                    String.format("요청 한도를 초과했습니다. %d초 후 다시 시도해주세요.", waitSeconds)
                );
            }

        } catch (CustomException e) {
            // 한도 초과 예외는 그대로 전파
            throw e;
        } catch (RedisConnectionFailureException | RedisSystemException e) {
            // Redis 장애 — Fail-Open (가용성 우선)
            log.error("[Rate Limit Fail-Open] Redis 장애로 검증 스킵: policy={}, key={}",
                policy.name(), redisKey, e);
        }
        // 그 외 예외는 의도치 않은 버그 → 그대로 전파 (조용히 실패 금지)
    }

    /**
     * 검증만 수행 (예외 없이 boolean 반환) — 분기 로직용
     */
    public boolean isAllowed(RateLimitPolicy policy, String identifier) {
        try {
            tryConsume(policy, identifier);
            return true;
        } catch (CustomException e) {
            if (e.getErrorCode() == ErrorCode.COMMON_009) {
                return false;
            }
            throw e;
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Private
    // ═══════════════════════════════════════════════════════

    /**
     * 로그용 식별자 마스킹 (PII 보호)
     */
    private String maskIdentifier(String identifier) {
        if (identifier == null) return "null";

        // 이메일 형식
        if (identifier.contains("@")) {
            int at = identifier.indexOf("@");
            String id = identifier.substring(0, at);
            String domain = identifier.substring(at);
            String maskedId = id.length() <= 2
                ? "*".repeat(id.length())
                : id.substring(0, 2) + "*".repeat(id.length() - 2);
            return maskedId + domain;
        }

        // IPv4 형식 (1.2.3.4 → 1.2.3.***)
        if (identifier.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            int last = identifier.lastIndexOf(".");
            return identifier.substring(0, last) + ".***";
        }

        // 그 외 (deviceId 등)
        if (identifier.length() > 8) {
            return identifier.substring(0, 4) + "***" + identifier.substring(identifier.length() - 4);
        }
        return identifier;
    }
}
