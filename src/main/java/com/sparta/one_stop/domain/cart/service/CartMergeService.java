package com.sparta.one_stop.domain.cart.service;

import com.sparta.one_stop.domain.cart.support.GuestCartRedisKeyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
     * - Redis ZSet 담기 순서를 기준으로 merge하여 50종 초과 시 선착순 merge를 보장
     * - ZSet이 없는 과거 Hash 데이터는 Hash 조회 결과 기준으로 뒤에 병합
     */
    public boolean mergeGuestCartToUserCart(
        Long userId,
        String guestCartId
    ) {
        if (guestCartId == null || guestCartId.isBlank()) {
            return false;
        }

        String redisKey = buildGuestCartKey(guestCartId);
        String orderKey = buildGuestCartOrderKey(guestCartId);

        Map<Object, Object> entries = redisTemplate.opsForHash()
            .entries(redisKey);

        if (entries.isEmpty()) {
            deleteRedisKey(redisKey);
            deleteRedisKey(orderKey);
            return true;
        }

        Set<String> orderedItemFields = redisTemplate.opsForZSet()
            .range(orderKey, 0, -1);

        Map<Long, Integer> guestQuantityMap = parseGuestCartEntries(
            entries,
            orderedItemFields
        );

        if (guestQuantityMap.isEmpty()) {
            deleteRedisKey(redisKey);
            deleteRedisKey(orderKey);
            return true;
        }

        cartMergeExecutor.execute(
            userId,
            guestQuantityMap
        );

        deleteRedisKey(redisKey);
        deleteRedisKey(orderKey);

        return true;
    }

    private String buildGuestCartKey(String guestCartId) {
        if (guestCartId == null || guestCartId.isBlank()) {
            throw new IllegalArgumentException("guestCartId는 필수입니다.");
        }

        return guestCartRedisKeyProvider.buildGuestCartKey(guestCartId);
    }

    private String buildGuestCartOrderKey(String guestCartId) {
        if (guestCartId == null || guestCartId.isBlank()) {
            throw new IllegalArgumentException("guestCartId는 필수입니다.");
        }

        return guestCartRedisKeyProvider.buildGuestCartOrderKey(guestCartId);
    }

    private Map<Long, Integer> parseGuestCartEntries(
        Map<Object, Object> entries,
        Set<String> orderedItemFields
    ) {
        Map<Long, Integer> rawQuantityMap = new HashMap<>();

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            try {
                Long itemId = Long.valueOf(entry.getKey().toString());
                Integer quantity = Integer.valueOf(entry.getValue().toString());

                rawQuantityMap.put(
                    itemId,
                    quantity
                );
            } catch (NumberFormatException e) {
                log.warn(
                    "Redis 장바구니 파싱 실패 - field={}, value={}",
                    entry.getKey(),
                    entry.getValue()
                );
            }
        }

        Map<Long, Integer> orderedQuantityMap = new LinkedHashMap<>();

        if (orderedItemFields != null && !orderedItemFields.isEmpty()) {
            for (String itemIdField : orderedItemFields) {
                try {
                    Long itemId = Long.valueOf(itemIdField);

                    if (rawQuantityMap.containsKey(itemId)) {
                        orderedQuantityMap.put(
                            itemId,
                            rawQuantityMap.get(itemId)
                        );
                    }
                } catch (NumberFormatException e) {
                    log.warn("Redis 장바구니 순서 파싱 실패 - itemId={}", itemIdField);
                }
            }
        }

        // ZSet에 없는 Hash 데이터 fallback 처리
        // 2차 구현에서 생성된 기존 Hash 데이터가 남아있는 경우 뒤에 병합
        rawQuantityMap.keySet()
            .stream()
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(itemId -> orderedQuantityMap.putIfAbsent(
                itemId,
                rawQuantityMap.get(itemId)
            ));

        return orderedQuantityMap;
    }

    private void deleteRedisKey(String redisKey) {
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Redis guest cart 삭제 실패: key={}", redisKey, e);
        }
    }

}
