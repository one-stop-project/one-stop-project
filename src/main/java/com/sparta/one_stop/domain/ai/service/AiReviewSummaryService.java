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
import com.sparta.one_stop.global.enums.review.ReviewStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
 * [증분 업데이트] lastIncludedReviewId < id ≤ newReviewId 범위 처리 (ORDER BY id ASC)
 *   - 리뷰 작성마다 이벤트 → 비동기 실행
 *   - 새 리뷰 수 < MIN_REVIEWS_FOR_INCREMENTAL(5) 시 스킵 (다음 이벤트까지 대기)
 *   - 새 리뷰 수 > MAX_REVIEW_FOR_INCREMENTAL(20) 시 전체 재요약으로 폴백
 *   - 내용 없는 리뷰만 있을 때 AI 스킵, lastIncludedReviewId/집계값은 갱신
 *   - 동시 충돌: @Version(낙관적 락) → 리스너에서 1회 재시도 후 포기
 *   - 최초 생성 경쟁: unique 제약 위반 시 리스너가 DataIntegrityViolationException 처리
 *
 * [조회] DB 요약 있으면 캐시된 count/avg 사용 (1 query), 없으면 PENDING 반환
 *   - AI 직접 호출 없음
 *
 * [커넥션 풀 보호] AI HTTP 호출(2~10초) 중 DB 커넥션을 점유하지 않도록
 *   비동기 메서드는 NOT_SUPPORTED로 TX를 배제하고, TransactionTemplate으로
 *   DB 읽기/쓰기 구간만 짧게 TX를 엽니다.
 *
 * [soft delete 대응] 모든 리뷰 조회·집계 쿼리에서 ACTIVE 상태만 필터
 *   - 삭제된(DELETED) 리뷰는 요약·카운트·평균 별점에서 제외
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiReviewSummaryService {

    private static final int MAX_REVIEW_FOR_FULL = 50;
    private static final int MAX_REVIEW_FOR_INCREMENTAL = 20;
    private static final int MIN_REVIEW_COUNT = 5;
    private static final int MIN_REVIEWS_FOR_INCREMENTAL = 5;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ReviewSummaryService reviewSummaryService;
    private final ProductReviewSummaryRepository summaryRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager txManager;

    private TransactionTemplate readTx;
    private TransactionTemplate writeTx;

    @PostConstruct
    void initTx() {
        TransactionTemplate rt = new TransactionTemplate(txManager);
        rt.setReadOnly(true);
        this.readTx = rt;
        this.writeTx = new TransactionTemplate(txManager);
    }

    /**
     * 요약 조회 — DB 요약이 없으면 AI를 호출하지 않고 PENDING을 반환합니다.
     */
    public AiReviewSummaryResponse getSummary(Long productId) {
        return summaryRepository.findByProduct_Id(productId)
            .map(e -> AiReviewSummaryResponse.ready(e.getReviewCount(), e.getAverageRating(), toReviewSummary(e)))
            .orElseGet(() -> {
                long reviewCount = reviewRepository.countByProduct_IdAndStatus(productId, ReviewStatus.ACTIVE);
                double averageRating = getAverageRating(productId);
                if (reviewCount < MIN_REVIEW_COUNT) {
                    return AiReviewSummaryResponse.insufficient(reviewCount, averageRating);
                }
                return AiReviewSummaryResponse.pending(reviewCount, averageRating);
            });
    }

    /**
     * 관리자 강제 갱신 (동기).
     * AI 호출 중 DB 커넥션을 점유하지 않도록 NOT_SUPPORTED + TransactionTemplate 사용.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AiReviewSummaryResponse refreshSummary(Long productId) {
        long[] counts = new long[1];
        readTx.executeWithoutResult(status -> {
            productRepository.findById(productId)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));
            counts[0] = reviewRepository.countByProduct_IdAndStatus(productId, ReviewStatus.ACTIVE);
        });

        if (counts[0] < MIN_REVIEW_COUNT) {
            double avg = getAverageRatingDirect(productId);
            return AiReviewSummaryResponse.insufficient(counts[0], avg);
        }

        doFullSummary(productId);

        return readTx.execute(status ->
            summaryRepository.findByProduct_Id(productId)
                .map(e -> AiReviewSummaryResponse.ready(e.getReviewCount(), e.getAverageRating(), toReviewSummary(e)))
                .orElseThrow(() -> new CustomException(ErrorCode.AI_001))
        );
    }

    /**
     * 리뷰 작성 이벤트 수신 후 증분 업데이트.
     * ReviewSummaryUpdateListener가 @Async("eventExecutor") + AFTER_COMMIT으로 호출합니다.
     * AI 호출 중 DB 커넥션을 점유하지 않도록 NOT_SUPPORTED + TransactionTemplate 사용.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateIncrementalSummary(Long productId, Long newReviewId) {
        IncrementalPayload payload = readTx.execute(status -> loadIncrementalPayload(productId, newReviewId));
        if (payload == null) return;

        if (payload.needsFullSummary()) {
            doFullSummary(productId);
            return;
        }

        // 내용 없는 리뷰만 있는 경우: AI 스킵, 경계값·집계값만 갱신
        if (payload.newReviewsText().isBlank()) {
            writeTx.executeWithoutResult(status ->
                summaryRepository.findByProduct_Id(productId).ifPresent(current ->
                    current.update(current.getPros(), current.getCons(), current.getKeywords(),
                        current.getSentiment(), payload.latestId(), payload.reviewCount(), payload.averageRating())
                )
            );
            return;
        }

        // AI 호출 — TX 없음, DB 커넥션 미점유
        ReviewSummary updated = reviewSummaryService.summarizeIncremental(payload.existingSummaryJson(), payload.newReviewsText());
        if (updated.isUnavailable()) return;

        writeTx.executeWithoutResult(status ->
            summaryRepository.findByProduct_Id(productId).ifPresent(current ->
                current.update(toJson(updated.pros()), toJson(updated.cons()),
                    toJson(updated.keywords()), updated.sentiment(),
                    payload.latestId(), payload.reviewCount(), payload.averageRating())
            )
        );
    }

    /**
     * 리뷰 수정/삭제 이벤트 수신 후 전체 재요약.
     * ReviewSummaryUpdateListener가 @Async("eventExecutor") + AFTER_COMMIT으로 호출합니다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refreshSummaryAsync(Long productId) {
        boolean hasEnough = Boolean.TRUE.equals(
            readTx.execute(status -> reviewRepository.countByProduct_IdAndStatus(productId, ReviewStatus.ACTIVE) >= MIN_REVIEW_COUNT)
        );

        if (!hasEnough) {
            writeTx.executeWithoutResult(status ->
                summaryRepository.findByProduct_Id(productId).ifPresent(summaryRepository::delete)
            );
            return;
        }

        doFullSummary(productId);
    }

    // ─────────────────────────────────────────────────────────────

    /**
     * 전체 요약: DB 읽기 → AI 호출 → DB 저장 을 분리해 커넥션 점유 최소화.
     */
    private void doFullSummary(Long productId) {
        FullSummaryPayload payload = readTx.execute(status -> loadFullSummaryPayload(productId));
        if (payload == null) return;

        // AI 호출 — TX 없음
        ReviewSummary summary = reviewSummaryService.summarize(payload.category(), payload.reviewsText());
        if (summary.isUnavailable()) return;

        writeTx.executeWithoutResult(status -> {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) return;
            summaryRepository.findByProduct_Id(productId).ifPresentOrElse(
                existing -> existing.update(
                    toJson(summary.pros()), toJson(summary.cons()),
                    toJson(summary.keywords()), summary.sentiment(),
                    payload.latestId(), payload.reviewCount(), payload.averageRating()),
                () -> summaryRepository.save(ProductReviewSummary.builder()
                    .product(product)
                    .pros(toJson(summary.pros()))
                    .cons(toJson(summary.cons()))
                    .keywords(toJson(summary.keywords()))
                    .sentiment(summary.sentiment())
                    .lastIncludedReviewId(payload.latestId())
                    .reviewCount(payload.reviewCount())
                    .averageRating(payload.averageRating())
                    .build())
            );
        });
    }

    private IncrementalPayload loadIncrementalPayload(Long productId, Long newReviewId) {
        long reviewCount = reviewRepository.countByProduct_IdAndStatus(productId, ReviewStatus.ACTIVE);
        if (reviewCount < MIN_REVIEW_COUNT) return null;

        ProductReviewSummary current = summaryRepository.findByProduct_Id(productId).orElse(null);
        if (current == null) return IncrementalPayload.fullSummary(reviewCount, getAverageRating(productId));

        long afterId = current.getLastIncludedReviewId() != null ? current.getLastIncludedReviewId() : 0L;
        List<Review> newReviews = reviewRepository.findNewReviewsBetween(productId, afterId, newReviewId, ReviewStatus.ACTIVE);
        if (newReviews.isEmpty()) return null;

        if (newReviews.size() < MIN_REVIEWS_FOR_INCREMENTAL) {
            log.debug("[AI Summary] 새 리뷰 {}건 — 임계값({}) 미달, 스킵: productId={}",
                newReviews.size(), MIN_REVIEWS_FOR_INCREMENTAL, productId);
            return null;
        }

        if (newReviews.size() > MAX_REVIEW_FOR_INCREMENTAL) {
            log.info("[AI Summary] 증분 한도 초과({}건), 전체 재요약 폴백: productId={}", newReviews.size(), productId);
            return IncrementalPayload.fullSummary(reviewCount, getAverageRating(productId));
        }

        long latestId = newReviews.stream().mapToLong(Review::getId).max().getAsLong();
        double averageRating = getAverageRating(productId);

        String newReviewsText = newReviews.stream()
            .map(r -> r.getContent() != null ? r.getContent() : "")
            .filter(c -> !c.isBlank())
            .collect(Collectors.joining("\n"));

        return IncrementalPayload.incremental(buildSummaryJson(current), newReviewsText, latestId, reviewCount, averageRating);
    }

    private FullSummaryPayload loadFullSummaryPayload(Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) return null;

        long reviewCount = reviewRepository.countByProduct_IdAndStatus(productId, ReviewStatus.ACTIVE);
        double averageRating = getAverageRating(productId);

        List<Review> reviews = reviewRepository.findAllByProduct_IdAndStatusOrderByCreatedAtDesc(
            productId, ReviewStatus.ACTIVE, PageRequest.of(0, MAX_REVIEW_FOR_FULL));

        String reviewsText = reviews.stream()
            .map(r -> r.getContent() != null ? r.getContent() : "")
            .filter(c -> !c.isBlank())
            .collect(Collectors.joining("\n"));

        if (reviewsText.isBlank()) return null;

        Long latestId = reviews.stream().mapToLong(Review::getId).max().stream().boxed().findFirst().orElse(null);
        return new FullSummaryPayload(mapToReviewCategory(product), reviewsText, latestId, reviewCount, averageRating);
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
        Double avg = reviewRepository.findAverageRatingByProductIdAndStatus(productId, ReviewStatus.ACTIVE);
        return avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0;
    }

    // TX 없이 직접 호출용 (NOT_SUPPORTED 컨텍스트에서 단순 조회)
    private double getAverageRatingDirect(Long productId) {
        return readTx.execute(status -> {
            Double avg = reviewRepository.findAverageRatingByProductIdAndStatus(productId, ReviewStatus.ACTIVE);
            return avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0;
        });
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

    private record IncrementalPayload(
        boolean needsFullSummary,
        String existingSummaryJson,
        String newReviewsText,
        long latestId,
        long reviewCount,
        double averageRating
    ) {
        static IncrementalPayload fullSummary(long reviewCount, double averageRating) {
            return new IncrementalPayload(true, null, null, 0, reviewCount, averageRating);
        }

        static IncrementalPayload incremental(String summaryJson, String reviewsText,
                                              long latestId, long reviewCount, double averageRating) {
            return new IncrementalPayload(false, summaryJson, reviewsText, latestId, reviewCount, averageRating);
        }
    }

    private record FullSummaryPayload(
        ReviewCategoryType category,
        String reviewsText,
        Long latestId,
        long reviewCount,
        double averageRating
    ) {}
}
