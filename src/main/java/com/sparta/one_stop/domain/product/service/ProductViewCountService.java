package com.sparta.one_stop.domain.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

// 상품 조회수 Redis 집계 — Lua 스크립트 호출 + 키 관리
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductViewCountService {

    private static final long DEDUP_TTL_SECONDS = 300L; // 5분
    private static final String COUNTER_KEY_PREFIX = "viewcount:product:";
    private static final String DEDUP_KEY_PREFIX = "viewcount:dedup:";
    private static final String DIRTY_KEY = "viewcount:dirty";

    // dedup 신규 진입 시에만 INCR + dirty 마킹, dedup 충돌이면 0 반환
    private static final RedisScript<Long> RECORD_VIEW_LOGIN_SCRIPT = RedisScript.of("""
        if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[2]) then
            redis.call('INCR', KEYS[2])
            redis.call('SADD', KEYS[3], ARGV[1])
            return 1
        end
        return 0
        """, Long.class);

    // 비로그인은 dedup 없이 매번 카운트
    private static final RedisScript<Long> RECORD_VIEW_GUEST_SCRIPT = RedisScript.of("""
        redis.call('INCR', KEYS[1])
        redis.call('SADD', KEYS[2], ARGV[1])
        return 1
        """, Long.class);

    // 누적 카운트를 가져오면서 counter/dirty를 Redis에서 원자적으로 제거
    private static final RedisScript<Long> SYNC_VIEW_COUNT_SCRIPT = RedisScript.of("""
        local count = redis.call('GET', KEYS[1])
        if count then
            redis.call('DEL', KEYS[1])
            redis.call('SREM', KEYS[2], ARGV[1])
            return tonumber(count)
        end
        return 0
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    // Redis 장애가 상품 조회 자체를 막지 않도록 catch 후 skip
    public void recordView(Long productId, Long userId) {
        try {
            if (userId != null) {
                List<String> keys = List.of(
                    DEDUP_KEY_PREFIX + productId + ":" + userId,
                    COUNTER_KEY_PREFIX + productId,
                    DIRTY_KEY
                );
                redisTemplate.execute(
                    RECORD_VIEW_LOGIN_SCRIPT,
                    keys,
                    productId.toString(),
                    String.valueOf(DEDUP_TTL_SECONDS)
                );
            } else {
                List<String> keys = List.of(
                    COUNTER_KEY_PREFIX + productId,
                    DIRTY_KEY
                );
                redisTemplate.execute(
                    RECORD_VIEW_GUEST_SCRIPT,
                    keys,
                    productId.toString()
                );
            }
        } catch (Exception e) {
            log.warn("[ViewCount] recordView failed (productId={}, userId={}): {}",
                productId, userId, e.getMessage());
        }
    }

    public Set<String> getDirtyProductIds() {
        Set<String> ids = redisTemplate.opsForSet().members(DIRTY_KEY);
        return ids != null ? ids : Set.of();
    }

    // 호출 즉시 counter/dirty가 Redis에서 제거됨 — 반환값 처리에 실패해도 복구 불가
    public long flushAndGetCount(Long productId) {
        List<String> keys = List.of(
            COUNTER_KEY_PREFIX + productId,
            DIRTY_KEY
        );
        Long count = redisTemplate.execute(
            SYNC_VIEW_COUNT_SCRIPT,
            keys,
            productId.toString()
        );
        return count != null ? count : 0L;
    }
}
