package com.sparta.one_stop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.sparta.one_stop.domain.product.dto.response.PopularTagResponse;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

@ExtendWith(MockitoExtension.class)
@DisplayName("PopularTagService - 인기 태그 자동완성")
class PopularTagServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private PopularTagService popularTagService;

    // 저장 태그는 소문자 — Redis ZSet 결과를 흉내낸다 (점수 내림차순 보존 위해 LinkedHashSet)
    private Set<TypedTuple<String>> tuples(String... values) {
        Set<TypedTuple<String>> set = new LinkedHashSet<>();
        double score = values.length;
        for (String v : values) {
            set.add(new DefaultTypedTuple<>(v, score--));
        }
        return set;
    }

    @Nested
    @DisplayName("Redis 경로")
    class RedisPath {

        @Test
        @DisplayName("대문자 prefix(\"Nike\")로 입력해도 소문자 저장 태그(\"nike\")가 매칭된다 (#450)")
        void uppercasePrefix_matchesLowercaseTag() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .willReturn(tuples("nike", "adidas", "newbalance"));

            List<PopularTagResponse> result = popularTagService.getAutocompleteTags("Nike", 10);

            assertThat(result).extracting(PopularTagResponse::tag).containsExactly("nike");
        }

        @Test
        @DisplayName("혼합 대소문자 prefix(\"NeW\")도 정규화되어 매칭된다")
        void mixedCasePrefix_matches() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .willReturn(tuples("nike", "newbalance"));

            List<PopularTagResponse> result = popularTagService.getAutocompleteTags("NeW", 10);

            assertThat(result).extracting(PopularTagResponse::tag).containsExactly("newbalance");
        }

        @Test
        @DisplayName("prefix가 null이면 전체를 반환한다")
        void nullPrefix_returnsAll() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .willReturn(tuples("nike", "adidas"));

            List<PopularTagResponse> result = popularTagService.getAutocompleteTags(null, 10);

            assertThat(result).extracting(PopularTagResponse::tag).containsExactly("nike", "adidas");
        }

        @Test
        @DisplayName("앞뒤 공백이 있는 대문자 prefix(\" NI \")도 trim·소문자화되어 매칭된다")
        void paddedUppercasePrefix_trimmedAndMatched() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            given(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .willReturn(tuples("nike", "adidas"));

            List<PopularTagResponse> result = popularTagService.getAutocompleteTags(" NI ", 10);

            assertThat(result).extracting(PopularTagResponse::tag).containsExactly("nike");
        }

        @Test
        @DisplayName("동점(같은 사용 횟수)이면 태그명 오름차순으로 반환한다 — Redis 역순 보정 (#487)")
        void tiedTags_sortedByTagAsc() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            // Redis ZSet은 동점 멤버를 사전식 역순(banana→apple)으로 준다고 가정한다
            Set<TypedTuple<String>> redisOrder = new LinkedHashSet<>();
            redisOrder.add(new DefaultTypedTuple<>("cherry", 10.0));
            redisOrder.add(new DefaultTypedTuple<>("banana", 5.0));
            redisOrder.add(new DefaultTypedTuple<>("apple", 5.0));
            given(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .willReturn(redisOrder);

            List<PopularTagResponse> result = popularTagService.getAutocompleteTags(null, 10);

            // 사용 횟수 DESC, 동점이면 태그명 ASC → cherry, apple, banana
            assertThat(result).extracting(PopularTagResponse::tag)
                .containsExactly("cherry", "apple", "banana");
        }

        @Test
        @DisplayName("limit 경계가 동점을 자르면 태그명 ASC 우선순위가 적용된다 (#487)")
        void tieAtLimitBoundary_keepsTagAscPriority() {
            given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
            Set<TypedTuple<String>> redisOrder = new LinkedHashSet<>();
            redisOrder.add(new DefaultTypedTuple<>("cherry", 10.0));
            redisOrder.add(new DefaultTypedTuple<>("banana", 5.0));
            redisOrder.add(new DefaultTypedTuple<>("apple", 5.0));
            given(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .willReturn(redisOrder);

            List<PopularTagResponse> result = popularTagService.getAutocompleteTags(null, 2);

            // 동점(5) 중 태그명 ASC로 apple이 banana보다 우선 → [cherry, apple]
            assertThat(result).extracting(PopularTagResponse::tag).containsExactly("cherry", "apple");
        }
    }

    @Nested
    @DisplayName("DB 폴백 경로 (Redis 장애)")
    class FallbackPath {

        @Test
        @DisplayName("Redis 장애 시 DB 폴백에서도 대문자 prefix가 소문자 태그를 매칭한다 (#450)")
        void uppercasePrefix_matchesInDbFallback() {
            given(redisTemplate.opsForZSet()).willThrow(new RuntimeException("redis down"));
            given(productRepository.findTopTags(anyInt())).willReturn(List.of(
                new Object[]{"nike", 5L},
                new Object[]{"adidas", 3L}));

            List<PopularTagResponse> result = popularTagService.getAutocompleteTags("NIKE", 10);

            assertThat(result).extracting(PopularTagResponse::tag).containsExactly("nike");
        }

        @Test
        @DisplayName("DB 폴백에서도 동점이면 태그명 오름차순으로 반환한다 (#487)")
        void tiedTags_sortedByTagAsc_inDbFallback() {
            given(redisTemplate.opsForZSet()).willThrow(new RuntimeException("redis down"));
            // 입력이 동점을 사전식 역순(banana→apple)으로 줘도 정책 순서로 보정되어야 한다
            given(productRepository.findTopTags(anyInt())).willReturn(List.of(
                new Object[]{"cherry", 10L},
                new Object[]{"banana", 5L},
                new Object[]{"apple", 5L}));

            List<PopularTagResponse> result = popularTagService.getAutocompleteTags(null, 10);

            assertThat(result).extracting(PopularTagResponse::tag)
                .containsExactly("cherry", "apple", "banana");
        }
    }
}
