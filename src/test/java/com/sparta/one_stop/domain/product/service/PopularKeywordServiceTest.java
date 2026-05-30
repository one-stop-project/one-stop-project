package com.sparta.one_stop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.product.event.SearchHistoryEvent;
import com.sparta.one_stop.domain.product.dto.response.PopularKeywordAdminResponse;
import com.sparta.one_stop.domain.product.dto.response.PopularKeywordResponse;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PopularKeywordServiceTest {

    @Mock
    RedisTemplate<String, String> redisTemplate;

    @Mock
    ZSetOperations<String, String> zSetOperations;

    @Mock
    ListOperations<String, String> listOperations;

    PopularKeywordService service;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        // ObjectMapper는 mock하지 않고 실제 인스턴스 사용 (LocalDateTime 직렬화 위해 jsr310 모듈 등록 필요)
        service = new PopularKeywordService(redisTemplate, objectMapper);
    }

    // ===== normalize (static, package-private) =====

    @Nested
    @DisplayName("normalize: 정규화 — trim + lowercase + 연속공백 압축, 길이<2는 null")
    class Normalize {

        @Test
        @DisplayName("null → null")
        void nullInput() {
            assertThat(PopularKeywordService.normalize(null)).isNull();
        }

        @Test
        @DisplayName("빈 문자열 → null")
        void empty() {
            assertThat(PopularKeywordService.normalize("")).isNull();
        }

        @Test
        @DisplayName("공백만 → null")
        void onlyWhitespace() {
            assertThat(PopularKeywordService.normalize("   ")).isNull();
        }

        @Test
        @DisplayName("1자 → null (길이 미달)")
        void oneChar() {
            assertThat(PopularKeywordService.normalize("A")).isNull();
        }

        @Test
        @DisplayName("앞뒤 공백 포함 1자 → null (trim 후 길이 미달)")
        void oneCharWithPadding() {
            assertThat(PopularKeywordService.normalize("  A  ")).isNull();
        }

        @Test
        @DisplayName("2자 → 소문자")
        void twoChar() {
            assertThat(PopularKeywordService.normalize("AB")).isEqualTo("ab");
        }

        @Test
        @DisplayName("대소문자 통일 — Macbook/MACBOOK/macbook 모두 macbook")
        void caseFolding() {
            assertThat(PopularKeywordService.normalize("Macbook")).isEqualTo("macbook");
            assertThat(PopularKeywordService.normalize("MACBOOK")).isEqualTo("macbook");
            assertThat(PopularKeywordService.normalize("macbook")).isEqualTo("macbook");
        }

        @Test
        @DisplayName("앞뒤 공백 제거 — '  Macbook  ' → 'macbook'")
        void trim() {
            assertThat(PopularKeywordService.normalize("  Macbook  ")).isEqualTo("macbook");
        }

        @Test
        @DisplayName("한글 단일 단어 — 대소문자 영향 없음")
        void koreanSingleWord() {
            assertThat(PopularKeywordService.normalize("맥북")).isEqualTo("맥북");
        }

        @Test
        @DisplayName("중간 공백 1칸은 유지 — '검정 운동화'")
        void singleMiddleSpace() {
            assertThat(PopularKeywordService.normalize("검정 운동화")).isEqualTo("검정 운동화");
        }

        @Test
        @DisplayName("중간 공백 여러 칸 → 1칸 압축")
        void multipleMiddleSpaces() {
            assertThat(PopularKeywordService.normalize("검정   운동화")).isEqualTo("검정 운동화");
        }

        @Test
        @DisplayName("전각 공백(　)도 정상 공백으로 압축")
        void fullwidthSpace() {
            assertThat(PopularKeywordService.normalize("검정　운동화")).isEqualTo("검정 운동화");
        }

        @Test
        @DisplayName("탭/개행 같은 모든 whitespace 문자도 압축")
        void mixedWhitespace() {
            assertThat(PopularKeywordService.normalize("검정\t\n운동화")).isEqualTo("검정 운동화");
        }

        @Test
        @DisplayName("100자 초과 → 정확히 100자로 잘림 (DB length=100 방어)")
        void clampsOver100() {
            String over = "a".repeat(150);
            String result = PopularKeywordService.normalize(over);
            assertThat(result).hasSize(100);
        }

        @Test
        @DisplayName("정확히 100자 → 그대로 유지")
        void exactly100() {
            String exact = "a".repeat(100);
            assertThat(PopularKeywordService.normalize(exact)).hasSize(100);
        }

        @Test
        @DisplayName("99자 → 그대로 유지 (자르지 않음)")
        void under100() {
            String under = "a".repeat(99);
            assertThat(PopularKeywordService.normalize(under)).hasSize(99);
        }
    }

    // ===== recordKeyword =====

    @Nested
    @DisplayName("recordKeyword: 정규화 후 null이면 스킵, 정상은 Lua 호출")
    class RecordKeyword {

        @Test
        @DisplayName("1자 키워드 → Redis 호출 안 함 (집계 스킵)")
        void skipShortKeyword() {
            service.recordKeyword("A", 1L);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("null 키워드 → Redis 호출 안 함")
        void skipNullKeyword() {
            service.recordKeyword(null, 1L);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("공백만 → Redis 호출 안 함")
        void skipBlankKeyword() {
            service.recordKeyword("   ", 1L);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("2자 키워드 → Lua 스크립트 호출 (정규화된 'ab'가 ARGV[1]로 전달)")
        void recordValid() {
            service.recordKeyword("AB", 1L);
            // ARGV: keyword, bucketTtl, payload, queueMaxLen (4개)
            then(redisTemplate).should()
                .execute(any(RedisScript.class), anyList(),
                    eq("ab"), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("비로그인(userId=null) → 정상 호출 ('macbook'으로 정규화)")
        void recordAnonymous() {
            service.recordKeyword("Macbook", null);
            then(redisTemplate).should()
                .execute(any(RedisScript.class), anyList(),
                    eq("macbook"), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Redis 예외 → swallow (호출자에 전파 X)")
        void swallowRedisError() {
            given(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("Redis down"));
            // 예외 전파 안 됨 = 정상 종료
            service.recordKeyword("Macbook", 1L);
        }
    }

    // ===== getPopularKeywords =====

    @Nested
    @DisplayName("getPopularKeywords: ZSET 응답 → rank 부여, 빈/장애 → 빈 리스트")
    class GetPopularKeywords {

        @Test
        @DisplayName("ZSET 정상 응답 → rank 1부터 매김")
        void mapsRanks() {
            Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
            tuples.add(new DefaultTypedTuple<>("macbook", 342.6));
            tuples.add(new DefaultTypedTuple<>("airpods", 215.4));
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(eq("popular:keyword:final"), eq(0L), eq(1L)))
                .willReturn(tuples);

            List<PopularKeywordResponse> result = service.getPopularKeywords(2);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getRank()).isEqualTo(1);
            assertThat(result.get(0).getKeyword()).isEqualTo("macbook");
            // count는 score 반올림
            assertThat(result.get(0).getCount()).isEqualTo(343L);
            assertThat(result.get(1).getRank()).isEqualTo(2);
            assertThat(result.get(1).getCount()).isEqualTo(215L);
        }

        @Test
        @DisplayName("ZSET 비어있음 → 빈 리스트")
        void emptyZset() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(9L)))
                .willReturn(Set.of());

            assertThat(service.getPopularKeywords(10)).isEmpty();
        }

        @Test
        @DisplayName("Redis 예외 → 빈 리스트 (장애 시 인기검색어 fallback 제외 정책)")
        void redisError() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(9L)))
                .willThrow(new RuntimeException("connection refused"));

            assertThat(service.getPopularKeywords(10)).isEmpty();
        }
    }

    // ===== getPopularKeywordsByDate =====

    @Nested
    @DisplayName("getPopularKeywordsByDate: 오늘 → final, 과거 → archive 키. 미존재 → keywords=[]")
    class GetByDate {

        @Test
        @DisplayName("오늘 날짜 → final 키 조회")
        void todayUsesFinal() {
            LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(eq("popular:keyword:final"), eq(0L), eq(19L)))
                .willReturn(Set.of());

            PopularKeywordAdminResponse resp = service.getPopularKeywordsByDate(today, 20);

            assertThat(resp.date()).isEqualTo(today.toString());
            assertThat(resp.keywords()).isEmpty();
            then(zSetOperations).should()
                .reverseRangeWithScores(eq("popular:keyword:final"), eq(0L), eq(19L));
        }

        @Test
        @DisplayName("과거 날짜 → archive 키(yyyyMMdd) 조회")
        void pastUsesArchive() {
            LocalDate past = LocalDate.of(2026, 5, 25);
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(
                eq("popular:keyword:final:20260525"), eq(0L), eq(19L)))
                .willReturn(Set.of());

            PopularKeywordAdminResponse resp = service.getPopularKeywordsByDate(past, 20);

            assertThat(resp.date()).isEqualTo("2026-05-25");
            assertThat(resp.keywords()).isEmpty();
        }

        @Test
        @DisplayName("Redis 예외 → keywords=[] (응답 자체는 정상)")
        void errorReturnsEmpty() {
            LocalDate past = LocalDate.of(2026, 5, 25);
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(19L)))
                .willThrow(new RuntimeException("Redis down"));

            PopularKeywordAdminResponse resp = service.getPopularKeywordsByDate(past, 20);

            assertThat(resp.date()).isEqualTo("2026-05-25");
            assertThat(resp.keywords()).isEmpty();
        }
    }

    // ===== refresh =====

    @Nested
    @DisplayName("refresh: 빈 결과 → final DELETE / 정상 → TOP_N 잘라 RENAME")
    class Refresh {

        @Test
        @DisplayName("모든 버킷 비어 weighted_tmp 미생성 → final DELETE 후 종료")
        void emptyBucketsDeletesFinal() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(redisTemplate.hasKey("popular:keyword:weighted:tmp")).willReturn(false);

            service.refresh();

            then(redisTemplate).should().delete("popular:keyword:final");
            // RENAME/EXPIRE는 호출되지 않아야 함
            then(redisTemplate).should(never()).rename(anyString(), anyString());
        }

        @Test
        @DisplayName("정상 빌드 — TOP_N 자르기 + RENAME + EXPIRE 순서")
        void normalBuild() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(redisTemplate.hasKey("popular:keyword:weighted:tmp")).willReturn(true);

            service.refresh();

            // TOP 50만 유지 — removeRange(0, -51)
            then(zSetOperations).should()
                .removeRange(eq("popular:keyword:weighted:tmp"), eq(0L), eq(-51L));
            then(redisTemplate).should()
                .rename("popular:keyword:weighted:tmp", "popular:keyword:final");
            then(redisTemplate).should().expire(eq("popular:keyword:final"), any());
        }

        @Test
        @DisplayName("ZUNIONSTORE 예외 → catch (다음 사이클 재시도)")
        void unionStoreError() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.unionAndStore(anyString(), anyList(), anyString(), any(), any()))
                .willThrow(new RuntimeException("Redis down"));

            // 예외 전파 X
            service.refresh();
        }
    }

    // ===== archiveAndReset =====

    @Nested
    @DisplayName("archiveAndReset: final 존재 시 RENAME + refresh, 미존재 시 RENAME skip")
    class ArchiveAndReset {

        @Test
        @DisplayName("final 존재 → archive 키로 RENAME + TTL + refresh 즉시 호출")
        void archivesAndRefreshes() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(redisTemplate.hasKey("popular:keyword:final")).willReturn(true);
            // refresh가 내부적으로 hasKey(weighted_tmp) 호출 — false로 빠르게 종료
            given(redisTemplate.hasKey("popular:keyword:weighted:tmp")).willReturn(false);

            service.archiveAndReset(LocalDate.of(2026, 5, 29));

            then(redisTemplate).should()
                .rename("popular:keyword:final", "popular:keyword:final:20260529");
            then(redisTemplate).should()
                .expire(eq("popular:keyword:final:20260529"), any());
            // refresh 호출됨 — weighted_tmp 비어있으니 final DELETE까지 진행
            then(redisTemplate).should().delete("popular:keyword:final");
        }

        @Test
        @DisplayName("final 미존재 → RENAME 시도 안 함, refresh는 호출됨")
        void skipRenameWhenAbsent() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(redisTemplate.hasKey("popular:keyword:final")).willReturn(false);
            given(redisTemplate.hasKey("popular:keyword:weighted:tmp")).willReturn(false);

            service.archiveAndReset(LocalDate.of(2026, 5, 29));

            then(redisTemplate).should(never()).rename(anyString(), anyString());
            // refresh는 호출돼야 함 (빈 인기검색어 시간 최소화)
            then(redisTemplate).should().delete("popular:keyword:final");
        }
    }

    // ===== peek/ack History batch =====

    @Nested
    @DisplayName("peekHistoryBatch/ackHistoryBatch: rawCount 기반 ack로 큐 정체 방지")
    class HistoryQueue {

        @Test
        @DisplayName("빈 LIST → rawCount=0, events 비어있음")
        void emptyQueue() {
            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(listOperations.range("search:history:queue", 0, 499L)).willReturn(List.of());

            PopularKeywordService.HistoryBatch batch = service.peekHistoryBatch(500);
            assertThat(batch.rawCount()).isZero();
            assertThat(batch.events()).isEmpty();
        }

        @Test
        @DisplayName("정상 N개 → rawCount=N, events=N")
        void allValid() throws Exception {
            String j1 = objectMapper.writeValueAsString(
                new SearchHistoryEvent("macbook", 1L, java.time.LocalDateTime.now()));
            String j2 = objectMapper.writeValueAsString(
                new SearchHistoryEvent("airpods", null, java.time.LocalDateTime.now()));
            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(listOperations.range("search:history:queue", 0, 499L))
                .willReturn(List.of(j1, j2));

            PopularKeywordService.HistoryBatch batch = service.peekHistoryBatch(500);
            assertThat(batch.rawCount()).isEqualTo(2);
            assertThat(batch.events()).hasSize(2);
        }

        @Test
        @DisplayName("일부 파싱 실패 → rawCount=raw 전체, events는 성공분만 (ack은 rawCount 기준이라 깨진 것도 제거됨)")
        void partialFail() throws Exception {
            String valid = objectMapper.writeValueAsString(
                new SearchHistoryEvent("macbook", 1L, java.time.LocalDateTime.now()));
            String invalid = "{this-is-not-valid-json";
            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(listOperations.range("search:history:queue", 0, 499L))
                .willReturn(List.of(valid, invalid));

            PopularKeywordService.HistoryBatch batch = service.peekHistoryBatch(500);
            // rawCount는 읽은 원소 수(2) — 깨진 1개도 포함해야 ack 시 큐에서 제거됨
            assertThat(batch.rawCount()).isEqualTo(2);
            assertThat(batch.events()).hasSize(1);
            assertThat(batch.events().get(0).keyword()).isEqualTo("macbook");
        }

        @Test
        @DisplayName("전부 파싱 실패(all-fail) → rawCount=raw, events 비어있음 (정체 방지의 핵심)")
        void allFail() {
            String bad1 = "{broken-1";
            String bad2 = "{broken-2";
            given(redisTemplate.opsForList()).willReturn(listOperations);
            given(listOperations.range("search:history:queue", 0, 499L))
                .willReturn(List.of(bad1, bad2));

            PopularKeywordService.HistoryBatch batch = service.peekHistoryBatch(500);
            // events는 비었지만 rawCount=2 → scheduler가 ack(2)로 깨진 2개를 큐에서 제거 → 정체 안 남
            assertThat(batch.rawCount()).isEqualTo(2);
            assertThat(batch.events()).isEmpty();
        }

        @Test
        @DisplayName("ackHistoryBatch(0) → LTRIM 호출 안 함")
        void noAckOnZero() {
            service.ackHistoryBatch(0);
            then(redisTemplate).should(never()).opsForList();
        }

        @Test
        @DisplayName("ackHistoryBatch(n>0) → LTRIM(n, -1) 호출")
        void ackTrims() {
            given(redisTemplate.opsForList()).willReturn(listOperations);
            service.ackHistoryBatch(100);
            then(listOperations).should().trim("search:history:queue", 100, -1);
        }
    }
}
