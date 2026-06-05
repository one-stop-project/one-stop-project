package com.sparta.one_stop.domain.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.ai.dto.AiReviewSummaryResponse;
import com.sparta.one_stop.domain.ai.dto.ReviewCategoryType;
import com.sparta.one_stop.domain.ai.dto.ReviewSummary;
import com.sparta.one_stop.domain.ai.entity.ProductReviewSummary;
import com.sparta.one_stop.domain.ai.repository.ProductReviewSummaryRepository;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.review.entity.Review;
import com.sparta.one_stop.domain.review.repository.ReviewRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 리뷰 요약 정책
 *
 * [전체 요약] 최신순 최대 50건 → AI → DB 저장
 *   - 최초 생성: MIN_REVIEW_COUNT(5) 도달 시 리뷰 작성 이벤트로 자동 트리거 (비동기)
 *   - 강제 갱신: 관리자 /ai-summary/refresh → 기존 요약 덮어쓰기 (동기)
 *
 * [증분 업데이트] lastIncludedReviewId < id ≤ newReviewId 범위 처리
 *   - 리뷰 작성마다 이벤트 → 비동기 실행
 *   - 새 리뷰 수 > MAX_REVIEW_FOR_INCREMENTAL(20) 시 전체 재요약으로 폴백
 *   - 동시 충돌: @Version(낙관적 락) → OptimisticLockException → 다음 이벤트에서 자연 보정
 *
 * [조회] DB 요약 있으면 캐시된 count/avg 사용 (1 query), 없으면 PENDING 반환
 *   - AI 직접 호출 없음
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiReviewSummaryService {

    private static final int MAX_REVIEW_FOR_FULL = 50;
    private static final int MAX_REVIEW_FOR_INCREMENTAL = 20;
    private static final int MIN_REVIEW_COUNT = 5;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ReviewSummaryService reviewSummaryService;
    private final ProductReviewSummaryRepository summaryRepository;
    private final ObjectMapper objectMapper;

    /**
     * 요약 조회 — DB 요약이 없으면 AI를 호출하지 않고 PENDING을 반환합니다.
     * entity가 있을 때는 캐시된 reviewCount/averageRating을 사용하므로 DB 쿼리 1회입니다.
     */
    public AiReviewSummaryResponse getSummary(Long productId) {
        return summaryRepository.findByProduct_Id(productId)
            .map(e -> AiReviewSummaryResponse.ready(e.getReviewCount(), e.getAverageRating(), toReviewSummary(e)))
            .orElseGet(() -> {
                long reviewCount = reviewRepository.countByProduct_Id(productId);
                if (reviewCount < MIN_REVIEW_COUNT) {
                    return AiReviewSummaryResponse.insufficient(reviewCount);
                }
                return AiReviewSummaryResponse.pending(reviewCount, getAverageRating(productId));
            });
    }

    /**
     * 관리자 강제 갱신.
     * 기존 요약을 덮어써서 빈 상태 노출을 방지합니다.
     * 리뷰 부족 → INSUFFICIENT 반환.
     * AI 장애 + 기존 요약 없음 → AI_001 예외 반환.
     * AI 장애 + 기존 요약 있음 → 기존(stale) 요약 반환.
     */
    @Transactional
    public AiReviewSummaryResponse refreshSummary(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

        long reviewCount = reviewRepository.countByProduct_Id(productId);
        if (reviewCount < MIN_REVIEW_COUNT) {
            return AiReviewSummaryResponse.insufficient(reviewCount);
        }

        generateAndSaveFullSummary(product);

        return summaryRepository.findByProduct_Id(productId)
            .map(e -> AiReviewSummaryResponse.ready(e.getReviewCount(), e.getAverageRating(), toReviewSummary(e)))
            .orElseThrow(() -> new CustomException(ErrorCode.AI_001));
    }

    /**
     * 리뷰 작성 이벤트 수신 후 증분 업데이트.
     * ReviewSummaryUpdateListener가 @Async("eventExecutor") + AFTER_COMMIT으로 호출합니다.
     *
     * - 요약 없음 + MIN_REVIEW_COUNT 충족 → 전체 요약 최초 생성
     * - 요약 있음 + 새 리뷰 ≤ 20건 → 증분 AI 호출
     * - 요약 있음 + 새 리뷰 > 20건 → 전체 재요약으로 폴백
     */
    @Transactional
    public void updateIncrementalSummary(Long productId, Long newReviewId) {
        long reviewCount = reviewRepository.countByProduct_Id(productId);
        if (reviewCount < MIN_REVIEW_COUNT) return;

        ProductReviewSummary current = summaryRepository.findByProduct_Id(productId).orElse(null);

        if (current == null) {
            Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));
            generateAndSaveFullSummary(product);
            return;
        }

        long afterId = current.getLastIncludedReviewId() != null ? current.getLastIncludedReviewId() : 0L;
        List<Review> newReviews = reviewRepository.findNewReviewsBetween(productId, afterId, newReviewId);
        if (newReviews.isEmpty()) return;

        // 누적 과다 시 전체 재요약으로 폴백
        if (newReviews.size() > MAX_REVIEW_FOR_INCREMENTAL) {
            log.info("[AI Summary] 증분 한도 초과({}건), 전체 재요약으로 폴백: productId={}", newReviews.size(), productId);
            Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));
            generateAndSaveFullSummary(product);
            return;
        }

        String existingSummaryJson = buildSummaryJson(current);
        String newReviewsText = newReviews.stream()
            .map(r -> r.getContent() != null ? r.getContent() : "")
            .filter(c -> !c.isBlank())
            .collect(Collectors.joining("\n"));

        if (newReviewsText.isBlank()) return;

        ReviewSummary updated = reviewSummaryService.summarizeIncremental(existingSummaryJson, newReviewsText);
        if (updated.isUnavailable()) return;

        double averageRating = getAverageRating(productId);
        Long latestId = newReviews.get(newReviews.size() - 1).getId(); // ASC 정렬 → 마지막 = 최신
        current.update(toJson(updated.pros()), toJson(updated.cons()),
            toJson(updated.keywords()), updated.sentiment(), latestId, reviewCount, averageRating);
    }

    // ─────────────────────────────────────────────────────────────

    /**
     * 전체 요약을 생성하고 DB에 저장합니다. 반드시 쓰기 트랜잭션 컨텍스트에서 호출하세요.
     * AI UNAVAILABLE이면 아무것도 저장하지 않고 반환합니다.
     */
    private void generateAndSaveFullSummary(Product product) {
        Long productId = product.getId();
        long reviewCount = reviewRepository.countByProduct_Id(productId);
        double averageRating = getAverageRating(productId);

        List<Review> reviews = reviewRepository.findAllByProduct_IdOrderByCreatedAtDesc(
            productId, PageRequest.of(0, MAX_REVIEW_FOR_FULL));

        String reviewsText = reviews.stream()
            .map(r -> r.getContent() != null ? r.getContent() : "")
            .filter(c -> !c.isBlank())
            .collect(Collectors.joining("\n"));

        if (reviewsText.isBlank()) return;

        ReviewSummary summary = reviewSummaryService.summarize(mapToReviewCategory(product), reviewsText);
        if (summary.isUnavailable()) return;

        Long latestId = reviews.isEmpty() ? null : reviews.get(0).getId(); // DESC → index 0 = 최신

        summaryRepository.findByProduct_Id(productId).ifPresentOrElse(
            existing -> existing.update(toJson(summary.pros()), toJson(summary.cons()),
                toJson(summary.keywords()), summary.sentiment(), latestId, reviewCount, averageRating),
            () -> summaryRepository.save(ProductReviewSummary.builder()
                .product(product)
                .pros(toJson(summary.pros()))
                .cons(toJson(summary.cons()))
                .keywords(toJson(summary.keywords()))
                .sentiment(summary.sentiment())
                .lastIncludedReviewId(latestId)
                .reviewCount(reviewCount)
                .averageRating(averageRating)
                .build())
        );
    }

    private ReviewSummary toReviewSummary(ProductReviewSummary entity) {
        return new ReviewSummary(
            parseJsonList(entity.getPros()),
            parseJsonList(entity.getCons()),
            parseJsonList(entity.getKeywords()),
            entity.getSentiment()
        );
    }

    private String buildSummaryJson(ProductReviewSummary entity) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("pros", parseJsonList(entity.getPros()));
            map.put("cons", parseJsonList(entity.getCons()));
            map.put("keywords", parseJsonList(entity.getKeywords()));
            map.put("sentiment", entity.getSentiment());
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("[AI Summary] 기존 요약 직렬화 실패 productId={}", entity.getProduct().getId(), e);
            return "{}";
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> parseJsonList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private double getAverageRating(Long productId) {
        Double avg = reviewRepository.findAverageRatingByProductId(productId);
        if (avg == null) return 0.0;
        return Math.round(avg * 10.0) / 10.0;
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
