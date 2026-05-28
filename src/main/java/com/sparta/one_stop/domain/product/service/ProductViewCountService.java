package com.sparta.one_stop.domain.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // dedup 신규 진입 시에만 INCR + dirty 마킹 + 인기상품 시간 버킷 ZINCRBY
    // dedup 충돌이면 0 반환
    private static final RedisScript<Long> RECORD_VIEW_LOGIN_SCRIPT = RedisScript.of("""
        if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[2]) then
            redis.call('INCR', KEYS[2])
            redis.call('SADD', KEYS[3], ARGV[1])
            redis.call('ZINCRBY', KEYS[4], 1, ARGV[1])
            redis.call('EXPIRE', KEYS[4], ARGV[3])
            return 1
        end
        return 0
        """, Long.class);

    // 비로그인은 dedup 없이 매번 카운트 + 인기상품 시간 버킷 ZINCRBY
    private static final RedisScript<Long> RECORD_VIEW_GUEST_SCRIPT = RedisScript.of("""
        redis.call('INCR', KEYS[1])
        redis.call('SADD', KEYS[2], ARGV[1])
        redis.call('ZINCRBY', KEYS[3], 1, ARGV[1])
        redis.call('EXPIRE', KEYS[3], ARGV[2])
        return 1
        """, Long.class);

    // DB UPDATE 성공 후 호출 — DECRBY로 처리한 만큼만 차감, 0 이하가 되면 cleanup
    // PEEK 이후 새로 들어온 INCR이 있어도 보존됨 (다음 사이클에서 처리)
    private static final RedisScript<Long> ACK_VIEW_COUNT_SCRIPT = RedisScript.of("""
        local remaining = redis.call('DECRBY', KEYS[1], ARGV[2])
        if remaining <= 0 then
            redis.call('DEL', KEYS[1])
            redis.call('SREM', KEYS[2], ARGV[1])
        end
        return remaining
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    // Redis 장애가 상품 조회 자체를 막지 않도록 catch 후 skip
    public void recordView(Long productId, Long userId) {
        try {
            String bucketKey = PopularProductService.viewBucketKey(LocalDateTime.now(KST));
            String bucketTtl = String.valueOf(PopularProductService.VIEW_BUCKET_TTL_SECONDS);

            if (userId != null) {
                List<String> keys = List.of(
                    DEDUP_KEY_PREFIX + productId + ":" + userId,
                    COUNTER_KEY_PREFIX + productId,
                    DIRTY_KEY,
                    bucketKey
                );
                redisTemplate.execute(
                    RECORD_VIEW_LOGIN_SCRIPT,
                    keys,
                    productId.toString(),
                    String.valueOf(DEDUP_TTL_SECONDS),
                    bucketTtl
                );
            } else {
                List<String> keys = List.of(
                    COUNTER_KEY_PREFIX + productId,
                    DIRTY_KEY,
                    bucketKey
                );
                redisTemplate.execute(
                    RECORD_VIEW_GUEST_SCRIPT,
                    keys,
                    productId.toString(),
                    bucketTtl
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

    // 카운터 값만 읽음 (부수 효과 없음). DB UPDATE 전에 호출
    public long peekCount(Long productId) {
        String value = redisTemplate.opsForValue().get(COUNTER_KEY_PREFIX + productId);
        return value == null ? 0L : Long.parseLong(value);
    }

    // DB UPDATE 성공 후 호출 — Redis에서 처리한 만큼 차감
    public void acknowledge(Long productId, long count) {
        List<String> keys = List.of(
            COUNTER_KEY_PREFIX + productId,
            DIRTY_KEY
        );
        redisTemplate.execute(
            ACK_VIEW_COUNT_SCRIPT,
            keys,
            productId.toString(),
            String.valueOf(count)
        );
    }
}
