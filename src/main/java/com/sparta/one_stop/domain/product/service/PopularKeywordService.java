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
    private static final int MAX_KEYWORD_LENGTH = 100; // SearchHistory.keyword 컬럼 length=100 과 일치
    private static final int TOP_N              = 50;

    private static final double WEIGHT_OLDEST = 0.1;
    private static final double WEIGHT_MIDDLE = 0.3;
    private static final double WEIGHT_RECENT = 0.6;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAY_FMT  = DateTimeFormatter.ofPattern("yyyyMMdd");
    // 전각 공백 등 유니코드 공백까지 매칭 (UNICODE 플래그)
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private static final long QUEUE_MAX_LEN = 500_000L; // 큐에 쌓아둘 최대 개수

    // 검색 1건 집계 + 큐 적재. LTRIM으로 상한 유지 (sync 멈춰도 무한 증식 방지)
    private static final RedisScript<Long> RECORD_KEYWORD_SCRIPT = RedisScript.of("""
        redis.call('ZINCRBY', KEYS[1], 1, ARGV[1])
        redis.call('EXPIRE', KEYS[1], ARGV[2])
        redis.call('RPUSH', KEYS[2], ARGV[3])
        redis.call('LTRIM', KEYS[2], -tonumber(ARGV[4]), -1)
        return 1
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis 장애가 검색 자체를 막지 않도록 catch 후 skip
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
                payload,
                String.valueOf(QUEUE_MAX_LEN)
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

    // 5분마다 최근 3시간 가중 합산 → TOP_N → 노출용 키로 RENAME 교체
    public void refresh() {
        LocalDateTime now = LocalDateTime.now(KST);
        try {
            buildWeighted(now);

            Boolean exists = redisTemplate.hasKey(WEIGHTED_TMP_KEY);
            if (!Boolean.TRUE.equals(exists)) {
                // 최근 3시간 검색이 없으면 합산 키가 안 생김 → 노출용 키 삭제해 빈 결과로
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

    // rawCount=꺼낸 개수, events=정상 파싱분. 큐는 rawCount만큼 비운다 (깨진 게 앞을 막지 않게)
    public record HistoryBatch(int rawCount, List<SearchHistoryEvent> events) {
    }

    public HistoryBatch peekHistoryBatch(int batchSize) {
        List<String> raws = redisTemplate.opsForList().range(SEARCH_HISTORY_QUEUE, 0, batchSize - 1L);
        if (raws == null || raws.isEmpty()) {
            return new HistoryBatch(0, List.of());
        }
        List<SearchHistoryEvent> events = new ArrayList<>(raws.size());
        for (String raw : raws) {
            try {
                events.add(objectMapper.readValue(raw, SearchHistoryEvent.class));
            } catch (JsonProcessingException e) {
                // 깨진 건 버림 (rawCount엔 포함돼 큐에선 같이 제거됨)
                log.warn("[PopularKeyword] history payload parse failed, dropping: {}", e.getMessage());
            }
        }
        return new HistoryBatch(raws.size(), events);
    }

    // DB 저장 성공 후 호출 — 처리한 개수만큼 큐 앞에서 제거
    public void ackHistoryBatch(int processedCount) {
        if (processedCount <= 0) return;
        redisTemplate.opsForList().trim(SEARCH_HISTORY_QUEUE, processedCount, -1);
    }

    public static String keywordBucketKey(LocalDateTime kstTime) {
        return KEYWORD_BUCKET_PREFIX + kstTime.format(HOUR_FMT);
    }

    // 집계용 정규화. 집계 제외 대상(빈 값/2자 미만)은 null 반환
    static String normalize(String raw) {
        if (raw == null) return null;
        // strip()은 trim()과 달리 전각 공백까지 제거
        String stripped = raw.strip();
        if (stripped.isEmpty()) return null;
        // 소문자 통일. ROOT 로케일이라 환경 무관 동일 결과
        String lower = stripped.toLowerCase(java.util.Locale.ROOT);
        // 가운데 연속 공백 → 한 칸
        String compact = MULTI_WHITESPACE.matcher(lower).replaceAll(" ");
        if (compact.length() < MIN_KEYWORD_LENGTH) {
            return null;
        }
        // DB 컬럼 100자 제한 — 초과 시 잘라서 저장 실패 방지
        return compact.length() > MAX_KEYWORD_LENGTH ? compact.substring(0, MAX_KEYWORD_LENGTH) : compact;
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
