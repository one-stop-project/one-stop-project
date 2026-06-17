package com.sparta.one_stop.domain.product.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sparta.one_stop.domain.product.dto.response.PopularTagResponse;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularTagService {

    private static final String POPULAR_TAG_KEY = "popular:tag:top";
    private static final int TOP_N = 100;
    private static final long CACHE_TTL_SECONDS = 4 * 3600L;
    private static final String LOCAL_CACHE_KEY = "all";

    // 정책: 사용 횟수 DESC, 동점이면 태그명 ASC.
    // Redis ZSet(reverseRange)은 동점 멤버를 사전식 역순으로 주므로 노출 직전 이 기준으로 재정렬한다.
    private static final Comparator<PopularTagResponse> TAG_ORDER =
        Comparator.comparingLong(PopularTagResponse::usageCount).reversed()
            .thenComparing(PopularTagResponse::tag);

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;

    // Redis 장애 시 DB 직접 쏠림 완충용 로컬 캐시
    // Redis 정상 시에는 사용하지 않으며, Redis 예외 발생 경로에서만 조회/저장
    // 빈 리스트도 캐시해야 장애 중 "태그 0건" 상태에서 DB 반복 요청을 막을 수 있음
    private final Cache<String, List<PopularTagResponse>> localCache =
        Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(1)
            .build();

    // 승인된 상품의 태그를 집계해 Redis ZSet에 갱신
    // 임시 키에 적재 후 rename으로 원자적 교체 → 갱신 도중 빈 결과 노출 방지
    // 성공 시 로컬 캐시도 함께 갱신하여 Redis 장애 대비 데이터 확보
    public void refresh() {
        String tempKey = POPULAR_TAG_KEY + ":tmp";
        try {
            List<Object[]> rows = productRepository.findTopTags(TOP_N);
            List<PopularTagResponse> allTags = rows.stream()
                .map(row -> new PopularTagResponse((String) row[0], ((Number) row[1]).longValue()))
                .toList();

            if (rows.isEmpty()) {
                redisTemplate.delete(POPULAR_TAG_KEY);
                // 빈 리스트를 캐시 — 장애 중 "태그 0건" 상태에서 DB 반복 요청 방지
                localCache.put(LOCAL_CACHE_KEY, List.of());
                return;
            }

            redisTemplate.delete(tempKey);
            for (Object[] row : rows) {
                String tag = (String) row[0];
                long count = ((Number) row[1]).longValue();
                redisTemplate.opsForZSet().add(tempKey, tag, count);
            }
            redisTemplate.expire(tempKey, Duration.ofSeconds(CACHE_TTL_SECONDS));
            redisTemplate.rename(tempKey, POPULAR_TAG_KEY);

            // 로컬 캐시 동기화 — Redis 성공 시 함께 갱신
            localCache.put(LOCAL_CACHE_KEY, allTags);

            log.info("[PopularTag] refresh done. tags={}", rows.size());
        } catch (Exception e) {
            redisTemplate.delete(tempKey);
            log.error("[PopularTag] refresh failed", e);
        }
    }

    // prefix로 시작하는 인기 태그 자동완성 (prefix null/blank이면 전체 반환)
    // 흐름: Redis → (예외 발생 시) 로컬 캐시 → (없으면) DB 조회
    // Redis 정상이지만 빈 경우: 로컬 캐시를 타지 않고 DB 직접 조회 (stale 데이터 방지)
    public List<PopularTagResponse> getAutocompleteTags(String prefix, int limit) {
        // 태그는 저장 시 소문자(Locale.ROOT)로 정규화되므로 비교 prefix도 동일하게 정규화한다.
        // (정규화하지 않으면 "Nike" 입력 시 소문자 저장된 "nike"가 매칭되지 않음)
        String normalizedPrefix = normalizePrefix(prefix);
        try {
            Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(POPULAR_TAG_KEY, 0, TOP_N - 1L);

            if (tuples == null || tuples.isEmpty()) {
                // Redis 정상 + 빈 데이터 → 로컬 캐시 미사용, DB 직접 조회
                return fallbackFromDb(normalizedPrefix, limit);
            }

            return tuples.stream()
                .filter(t -> t.getValue() != null && t.getScore() != null)
                .filter(t -> normalizedPrefix == null || t.getValue().startsWith(normalizedPrefix))
                .map(t -> new PopularTagResponse(t.getValue(), Math.round(t.getScore())))
                .sorted(TAG_ORDER)
                .limit(limit)
                .toList();
        } catch (Exception e) {
            // Redis 예외(장애) → 로컬 캐시 우선 조회
            log.debug("[PopularTag] redis failed, fallback to local/db: {}", e.getMessage());
            return fallbackFromLocalOrDb(normalizedPrefix, limit);
        }
    }

    // prefix를 저장 태그와 동일하게 정규화 (trim + 소문자). null/blank이면 null(=전체 반환)
    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        return prefix.trim().toLowerCase(java.util.Locale.ROOT);
    }

    // prefix는 getAutocompleteTags에서 이미 정규화(trim+소문자)된 값을 받는다
    private List<PopularTagResponse> fallbackFromLocalOrDb(String normalizedPrefix, int limit) {
        List<PopularTagResponse> cached = localCache.getIfPresent(LOCAL_CACHE_KEY);
        if (cached != null) {
            // 빈 리스트도 유효한 캐시 결과 ("태그 0건" 상태를 DB 재요청 없이 반환)
            log.debug("[PopularTag] serving from local cache (Redis unavailable)");
            return filterAndLimit(cached, normalizedPrefix, limit);
        }
        return fallbackFromDb(normalizedPrefix, limit);
    }

    private List<PopularTagResponse> fallbackFromDb(String normalizedPrefix, int limit) {
        try {
            List<PopularTagResponse> all = productRepository.findTopTags(TOP_N).stream()
                .map(row -> new PopularTagResponse((String) row[0], ((Number) row[1]).longValue()))
                .toList();

            // 빈 리스트도 캐시 — 장애 중 0건 상태에서 DB 반복 쏠림 방지
            localCache.put(LOCAL_CACHE_KEY, all);
            log.debug("[PopularTag] local cache populated from DB fallback. tags={}", all.size());

            return filterAndLimit(all, normalizedPrefix, limit);
        } catch (Exception e) {
            log.warn("[PopularTag] DB fallback failed: {}", e.getMessage());
            return List.of();
        }
    }

    // normalizedPrefix는 이미 trim+소문자 정규화된 값 (null이면 전체 반환)
    private List<PopularTagResponse> filterAndLimit(List<PopularTagResponse> all, String normalizedPrefix, int limit) {
        return all.stream()
            .filter(r -> normalizedPrefix == null || r.tag().startsWith(normalizedPrefix))
            .sorted(TAG_ORDER)
            .limit(limit)
            .toList();
    }
}
