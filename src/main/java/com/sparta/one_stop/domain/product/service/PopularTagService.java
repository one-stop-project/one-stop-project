package com.sparta.one_stop.domain.product.service;

import com.sparta.one_stop.domain.product.dto.response.PopularTagResponse;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularTagService {

    private static final String POPULAR_TAG_KEY = "popular:tag:top";
    private static final int TOP_N = 100;
    private static final long CACHE_TTL_SECONDS = 4 * 3600L;

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;

    // 승인된 상품의 태그를 집계해 Redis ZSet에 갱신
    public void refresh() {
        try {
            List<Object[]> rows = productRepository.findTopTags(TOP_N);
            if (rows.isEmpty()) {
                redisTemplate.delete(POPULAR_TAG_KEY);
                return;
            }
            redisTemplate.delete(POPULAR_TAG_KEY);
            for (Object[] row : rows) {
                String tag = (String) row[0];
                long count = ((Number) row[1]).longValue();
                redisTemplate.opsForZSet().add(POPULAR_TAG_KEY, tag, count);
            }
            redisTemplate.expire(POPULAR_TAG_KEY, Duration.ofSeconds(CACHE_TTL_SECONDS));
            log.info("[PopularTag] refresh done. tags={}", rows.size());
        } catch (Exception e) {
            log.error("[PopularTag] refresh failed", e);
        }
    }

    // prefix로 시작하는 인기 태그 자동완성 (prefix null/blank이면 전체 반환)
    // Redis가 비어있으면 DB 직접 조회로 fallback
    public List<PopularTagResponse> getAutocompleteTags(String prefix, int limit) {
        try {
            Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(POPULAR_TAG_KEY, 0, TOP_N - 1L);

            if (tuples == null || tuples.isEmpty()) {
                return fallbackFromDb(prefix, limit);
            }

            return tuples.stream()
                .filter(t -> t.getValue() != null && t.getScore() != null)
                .filter(t -> prefix == null || prefix.isBlank() || t.getValue().startsWith(prefix.trim()))
                .limit(limit)
                .map(t -> new PopularTagResponse(t.getValue(), Math.round(t.getScore())))
                .toList();
        } catch (Exception e) {
            log.warn("[PopularTag] autocomplete redis failed, fallback to DB: {}", e.getMessage());
            return fallbackFromDb(prefix, limit);
        }
    }

    private List<PopularTagResponse> fallbackFromDb(String prefix, int limit) {
        try {
            return productRepository.findTopTags(TOP_N).stream()
                .filter(row -> {
                    String tag = (String) row[0];
                    return prefix == null || prefix.isBlank() || tag.startsWith(prefix.trim());
                })
                .limit(limit)
                .map(row -> new PopularTagResponse((String) row[0], ((Number) row[1]).longValue()))
                .toList();
        } catch (Exception e) {
            log.warn("[PopularTag] fallback DB query failed: {}", e.getMessage());
            return List.of();
        }
    }
}
