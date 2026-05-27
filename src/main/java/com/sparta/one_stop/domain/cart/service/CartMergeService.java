package com.sparta.one_stop.domain.cart.service;

import com.sparta.one_stop.domain.cart.support.GuestCartRedisKeyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartMergeService {

    private final RedisTemplate<String, String> redisTemplate;
    private final CartMergeExecutor cartMergeExecutor;
    private final GuestCartRedisKeyProvider guestCartRedisKeyProvider;

    /**
     * 비로그인 장바구니를 로그인 사용자의 DB 장바구니로 병합
     *
     * 반환값 의미:
     * - true: Redis 장바구니 merge/정리가 정상 처리되어 guest_cart_id 쿠키 삭제 가능
     * - false: guest_cart_id가 없거나 처리 대상이 없어 쿠키 삭제 불필요
     *
     * 주의:
     * - DB merge는 CartMergeExecutor의 트랜잭션 안에서 처리
     * - Redis 삭제는 DB merge 성공 후 트랜잭션 밖에서 처리
     * - merge 중 예외는 호출자인 AuthController에서 처리
     * - 2차 구현에서는 Redis Hash 조회 결과 기준으로 merge하며, 선착순 보장은 3차 구현에서 처리
     */
    public boolean mergeGuestCartToUserCart(
        Long userId,
        String guestCartId
    ) {
        if (guestCartId == null || guestCartId.isBlank()) {
            return false;
        }

        String redisKey = buildGuestCartKey(guestCartId);

        Map<Object, Object> entries = redisTemplate.opsForHash()
            .entries(redisKey);

        if (entries.isEmpty()) {
            deleteRedisKey(redisKey);
            return true;
        }

        Map<Long, Integer> guestQuantityMap = parseGuestCartEntries(entries);

        if (guestQuantityMap.isEmpty()) {
            deleteRedisKey(redisKey);
            return true;
        }

        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        deleteRedisKey(redisKey);

        return true;
    }

    private String buildGuestCartKey(String guestCartId) {
        if (guestCartId == null || guestCartId.isBlank()) {
            throw new IllegalArgumentException("guestCartId는 필수입니다.");
        }

        return guestCartRedisKeyProvider.buildGuestCartKey(guestCartId);
    }

    private Map<Long, Integer> parseGuestCartEntries(Map<Object, Object> entries) {
        Map<Long, Integer> result = new HashMap<>();

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            try {
                Long itemId = Long.valueOf(entry.getKey().toString());
                Integer quantity = Integer.valueOf(entry.getValue().toString());

                result.put(itemId, quantity);
            } catch (NumberFormatException e) {
                log.warn(
                    "Redis 장바구니 파싱 실패 - field={}, value={}",
                    entry.getKey(),
                    entry.getValue()
                );
            }
        }

        return result;
    }

    private void deleteRedisKey(String redisKey) {
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Redis guest cart 삭제 실패: key={}", redisKey, e);
        }
    }

}
