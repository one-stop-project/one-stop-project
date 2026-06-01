package com.sparta.one_stop.domain.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.ai.dto.AiReviewSummaryResponse;
import com.sparta.one_stop.domain.ai.dto.ReviewCategoryType;
import com.sparta.one_stop.domain.ai.dto.ReviewSummary;
import com.sparta.one_stop.domain.ai.repository.AiReviewReader;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.review.entity.Review;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiReviewSummaryService {

    private static final String CACHE_KEY_PREFIX = "ai:review-summary:";
    private static final int MIN_REVIEW_COUNT = 10;
    private static final int MAX_REVIEW_FOR_AI = 50; // 토큰 비용 및 컨텍스트 윈도우 초과 방지
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final AiReviewReader aiReviewReader;
    private final ProductRepository productRepository;
    private final ReviewSummaryService reviewSummaryService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public AiReviewSummaryResponse getSummary(Long productId) {
        long reviewCount = aiReviewReader.countByProduct_Id(productId);
        double averageRating = getAverageRating(productId);

        if (reviewCount < MIN_REVIEW_COUNT) {
            return AiReviewSummaryResponse.insufficient(reviewCount);
        }

        // 캐시 우선 조회
        ReviewSummary cached = getFromCache(productId);
        if (cached != null) {
            return AiReviewSummaryResponse.of(reviewCount, averageRating, cached);
        }

        // AI 호출
        List<Review> reviews = aiReviewReader.findAllByProduct_IdOrderByCreatedAtDesc(
            productId, PageRequest.of(0, MAX_REVIEW_FOR_AI));
        String reviewsText = reviews.stream()
            .map(Review::getContent)
            .collect(Collectors.joining("\n"));

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        ReviewCategoryType categoryType = mapToReviewCategory(product);
        ReviewSummary summary = reviewSummaryService.summarize(categoryType, reviewsText);

        // UNAVAILABLE이면 null 반환 (캐시도 저장 안 함)
        if ("UNAVAILABLE".equals(summary.sentiment())) {
            return AiReviewSummaryResponse.of(reviewCount, averageRating, null);
        }

        putToCache(productId, summary);
        return AiReviewSummaryResponse.of(reviewCount, averageRating, summary);
    }

    // 관리자 트리거: 캐시 무효화 후 AI 재호출
    @Transactional
    public AiReviewSummaryResponse refreshSummary(Long productId) {
        redisTemplate.delete(CACHE_KEY_PREFIX + productId);
        return getSummary(productId);
    }

    private double getAverageRating(Long productId) {
        Double avg = aiReviewReader.findAverageRatingByProductId(productId);
        if (avg == null) return 0.0;
        return Math.round(avg * 10.0) / 10.0;
    }

    private ReviewSummary getFromCache(Long productId) {
        String json = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + productId);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, ReviewSummary.class);
        } catch (JsonProcessingException e) {
            log.warn("[AI Cache] 역직렬화 실패 productId={}", productId, e);
            return null;
        }
    }

    private void putToCache(Long productId, ReviewSummary summary) {
        try {
            String json = objectMapper.writeValueAsString(summary);
            redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + productId, json, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("[AI Cache] 직렬화 실패 productId={}", productId, e);
        }
    }

    private ReviewCategoryType mapToReviewCategory(Product product) {
        String names = product.getCategoryMappings().stream()
            .map(m -> m.getCategory().getName().toLowerCase())
            .collect(Collectors.joining(" "));

        if (names.contains("의류") || names.contains("패션") || names.contains("옷")
            || names.contains("신발") || names.contains("가방")) {
            return ReviewCategoryType.CLOTHING;
        }
        if (names.contains("전자") || names.contains("디지털") || names.contains("가전")
            || names.contains("it") || names.contains("컴퓨터")) {
            return ReviewCategoryType.ELECTRONICS;
        }
        if (names.contains("식품") || names.contains("음식") || names.contains("식재료")
            || names.contains("과일") || names.contains("채소")) {
            return ReviewCategoryType.FOOD;
        }
        return ReviewCategoryType.GENERAL;
    }
}
