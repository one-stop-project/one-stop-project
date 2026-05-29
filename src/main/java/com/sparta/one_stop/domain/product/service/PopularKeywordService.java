package com.sparta.one_stop.domain.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.product.event.SearchHistoryEvent;
import com.sparta.one_stop.domain.product.dto.response.PopularKeywordAdminResponse;
import com.sparta.one_stop.domain.product.dto.response.PopularKeywordResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PopularKeywordService {

    public static final String KEYWORD_BUCKET_PREFIX = "popular:keyword:bucket:1h:";
    public static final String SEARCH_HISTORY_QUEUE  = "search:history:queue";
    private static final String FINAL_KEY            = "popular:keyword:final";
    private static final String WEIGHTED_TMP_KEY     = "popular:keyword:weighted:tmp";
    private static final String ARCHIVE_KEY_PREFIX   = "popular:keyword:final:";

    public static final long KEYWORD_BUCKET_TTL_SECONDS = 4 * 3600L;
    private static final long FINAL_TTL_SECONDS         = 600L;
    private static final long ARCHIVE_TTL_SECONDS       = 7 * 86400L;

    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int TOP_N              = 50;

    private static final double WEIGHT_OLDEST = 0.1;
    private static final double WEIGHT_MIDDLE = 0.3;
    private static final double WEIGHT_RECENT = 0.6;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAY_FMT  = DateTimeFormatter.ofPattern("yyyyMMdd");
    // 전각 공백(　) 등 Unicode whitespace까지 매칭해야 "검정　운동화" → "검정 운동화"로 압축됨
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    // ZINCRBY 버킷 + EXPIRE + RPUSH queue 를 원자적으로 처리
    private static final RedisScript<Long> RECORD_KEYWORD_SCRIPT = RedisScript.of("""
        redis.call('ZINCRBY', KEYS[1], 1, ARGV[1])
        redis.call('EXPIRE', KEYS[1], ARGV[2])
        redis.call('RPUSH', KEYS[2], ARGV[3])
        return 1
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // 검색 발생 시 컨트롤러에서 후처리 호출 — Redis 장애가 검색 자체를 막지 않도록 catch
    public void recordKeyword(String rawKeyword, Long userId) {
        String normalized = normalize(rawKeyword);
        if (normalized == null) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now(KST);
            SearchHistoryEvent event = new SearchHistoryEvent(normalized, userId, now);
            String payload = objectMapper.writeValueAsString(event);

            redisTemplate.execute(
                RECORD_KEYWORD_SCRIPT,
                List.of(keywordBucketKey(now), SEARCH_HISTORY_QUEUE),
                normalized,
                String.valueOf(KEYWORD_BUCKET_TTL_SECONDS),
                payload
            );
        } catch (JsonProcessingException e) {
            log.warn("[PopularKeyword] event serialize failed (keyword={}): {}", normalized, e.getMessage());
        } catch (Exception e) {
            log.warn("[PopularKeyword] recordKeyword failed (keyword={}, userId={}): {}",
                normalized, userId, e.getMessage());
        }
    }

    // 구매자 노출용 TOP — Redis 장애/빈 키 시 빈 리스트
    public List<PopularKeywordResponse> getPopularKeywords(int limit) {
        try {
            return fetchTop(FINAL_KEY, limit);
        } catch (Exception e) {
            log.warn("[PopularKeyword] redis fetch failed, returning empty: {}", e.getMessage());
            return List.of();
        }
    }

    // 관리자 date 조회 — 오늘이면 final, 과거면 archive
    public PopularKeywordAdminResponse getPopularKeywordsByDate(LocalDate date, int limit) {
        LocalDate today = LocalDate.now(KST);
        String key = date.equals(today) ? FINAL_KEY : archiveKey(date);
        List<PopularKeywordResponse> keywords;
        try {
            keywords = fetchTop(key, limit);
        } catch (Exception e) {
            log.warn("[PopularKeyword] admin fetch failed (date={}): {}", date, e.getMessage());
            keywords = List.of();
        }
        return new PopularKeywordAdminResponse(date.toString(), keywords);
    }

    // 5분 주기 — 3시간 윈도우 가중 합산 후 TOP_N 잘라 final로 atomic 교체
    public void refresh() {
        LocalDateTime now = LocalDateTime.now(KST);
        try {
            buildWeighted(now);

            Boolean exists = redisTemplate.hasKey(WEIGHTED_TMP_KEY);
            if (!Boolean.TRUE.equals(exists)) {
                // 모든 버킷이 비어있어 ZUNIONSTORE가 결과 키를 만들지 않음
                // 기존 final 무효화 → 빈 인기검색어 노출
                redisTemplate.delete(FINAL_KEY);
                return;
            }

            redisTemplate.opsForZSet().removeRange(WEIGHTED_TMP_KEY, 0, -TOP_N - 1L);
            redisTemplate.rename(WEIGHTED_TMP_KEY, FINAL_KEY);
            redisTemplate.expire(FINAL_KEY, Duration.ofSeconds(FINAL_TTL_SECONDS));

            log.info("[PopularKeyword] refresh done");
        } catch (Exception e) {
            log.error("[PopularKeyword] refresh failed", e);
        }
    }

    // 자정 cron — final을 yyyyMMdd 키로 보존 후 새 final 즉시 빌드
    public void archiveAndReset(LocalDate ymdKst) {
        try {
            Boolean exists = redisTemplate.hasKey(FINAL_KEY);
            if (Boolean.TRUE.equals(exists)) {
                String target = archiveKey(ymdKst);
                redisTemplate.rename(FINAL_KEY, target);
                redisTemplate.expire(target, Duration.ofSeconds(ARCHIVE_TTL_SECONDS));
                log.info("[PopularKeyword] archived final -> {}", target);
            }
        } catch (Exception e) {
            log.error("[PopularKeyword] archive failed (ymd={})", ymdKst, e);
        }
        refresh();
    }

    // SearchHistoryScheduler가 peek/ack 용도로 호출 — LIST 조작은 service 일관성 유지를 위해 한 곳에서
    public List<SearchHistoryEvent> peekHistoryBatch(int batchSize) {
        List<String> raws = redisTemplate.opsForList().range(SEARCH_HISTORY_QUEUE, 0, batchSize - 1L);
        if (raws == null || raws.isEmpty()) {
            return List.of();
        }
        List<SearchHistoryEvent> events = new ArrayList<>(raws.size());
        for (String raw : raws) {
            try {
                events.add(objectMapper.readValue(raw, SearchHistoryEvent.class));
            } catch (JsonProcessingException e) {
                // 잘못된 페이로드는 큐에 남아있으면 무한 재시도되므로 drop 로그만 남기고 스킵
                log.warn("[PopularKeyword] history payload parse failed, dropping: {}", e.getMessage());
            }
        }
        return events;
    }

    // LTRIM은 ack 의미 — DB INSERT 성공 후 호출
    public void ackHistoryBatch(int processedCount) {
        if (processedCount <= 0) return;
        redisTemplate.opsForList().trim(SEARCH_HISTORY_QUEUE, processedCount, -1);
    }

    public static String keywordBucketKey(LocalDateTime kstTime) {
        return KEYWORD_BUCKET_PREFIX + kstTime.format(HOUR_FMT);
    }

    // null/공백/길이 미달은 null 반환 (집계 스킵 신호)
    static String normalize(String raw) {
        if (raw == null) return null;
        // strip()은 trim()과 달리 Unicode whitespace(전각 공백 등)까지 제거
        String stripped = raw.strip();
        if (stripped.isEmpty()) return null;
        // 한글 대소문자 개념 없음 — ROOT 로케일로 안전한 lowercase
        String lower = stripped.toLowerCase(java.util.Locale.ROOT);
        // 모든 종류의 공백(전각 포함)을 단일 스페이스로 압축
        String compact = MULTI_WHITESPACE.matcher(lower).replaceAll(" ");
        return compact.length() < MIN_KEYWORD_LENGTH ? null : compact;
    }

    // ===== 내부 구현 =====

    private void buildWeighted(LocalDateTime now) {
        String oldest = keywordBucketKey(now.minusHours(2));
        String middle = keywordBucketKey(now.minusHours(1));
        String recent = keywordBucketKey(now);

        redisTemplate.opsForZSet().unionAndStore(
            oldest,
            List.of(middle, recent),
            WEIGHTED_TMP_KEY,
            Aggregate.SUM,
            Weights.of(WEIGHT_OLDEST, WEIGHT_MIDDLE, WEIGHT_RECENT)
        );
    }

    private List<PopularKeywordResponse> fetchTop(String key, int limit) {
        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, 0, limit - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<PopularKeywordResponse> result = new ArrayList<>(tuples.size());
        AtomicInteger rank = new AtomicInteger(1);
        for (TypedTuple<String> t : tuples) {
            String word = t.getValue();
            Double score = t.getScore();
            if (word == null || score == null) continue;
            result.add(PopularKeywordResponse.of(rank.getAndIncrement(), word, score));
        }
        return result;
    }

    private static String archiveKey(LocalDate kstDate) {
        return ARCHIVE_KEY_PREFIX + kstDate.format(DAY_FMT);
    }
}
