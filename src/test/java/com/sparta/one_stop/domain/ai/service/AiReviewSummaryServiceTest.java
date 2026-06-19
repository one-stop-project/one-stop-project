package com.sparta.one_stop.domain.ai.service;

import com.sparta.one_stop.domain.ai.dto.AiReviewSummaryResponse;
import com.sparta.one_stop.domain.ai.entity.ProductReviewSummary;
import com.sparta.one_stop.domain.ai.repository.ProductReviewSummaryRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.review.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiReviewSummaryService 테스트")
class AiReviewSummaryServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ReviewSummaryService reviewSummaryService;
    @Mock private ProductReviewSummaryRepository summaryRepository;
    @Mock private PlatformTransactionManager txManager;

    private AiReviewSummaryService service;

    private static final Long PRODUCT_ID = 1L;

    @BeforeEach
    void setUp() {
        TransactionStatus txStatus = mock(TransactionStatus.class);
        given(txManager.getTransaction(any())).willReturn(txStatus);

        service = new AiReviewSummaryService(
            reviewRepository, productRepository, reviewSummaryService,
            summaryRepository, new ObjectMapper(), txManager
        );
        service.initTx();
    }

    @Nested
    @DisplayName("getSummary()")
    class GetSummary {

        @Test
        @DisplayName("요약 DB에 존재 → READY 반환")
        void getSummary_returns_ready_when_summary_exists() {
            ProductReviewSummary entity = mock(ProductReviewSummary.class);
            given(entity.getReviewCount()).willReturn(10L);
            given(entity.getAverageRating()).willReturn(4.5);
            given(entity.getPros()).willReturn("[\"착용감 좋음\"]");
            given(entity.getCons()).willReturn("[]");
            given(entity.getKeywords()).willReturn("[\"착용감\"]");
            given(entity.getSentiment()).willReturn("POSITIVE");
            given(summaryRepository.findByProduct_Id(PRODUCT_ID)).willReturn(Optional.of(entity));

            AiReviewSummaryResponse result = service.getSummary(PRODUCT_ID);

            assertThat(result.status()).isEqualTo(AiReviewSummaryResponse.SummaryStatus.READY);
            assertThat(result.reviewCount()).isEqualTo(10L);
            assertThat(result.summary()).isNotNull();
        }

        @Test
        @DisplayName("요약 없음 + 리뷰 수 < 5 → INSUFFICIENT 반환")
        void getSummary_returns_insufficient_when_review_count_below_threshold() {
            given(summaryRepository.findByProduct_Id(PRODUCT_ID)).willReturn(Optional.empty());
            given(reviewRepository.countByProduct_Id(PRODUCT_ID)).willReturn(3L);
            given(reviewRepository.findAverageRatingByProductId(PRODUCT_ID)).willReturn(null);

            AiReviewSummaryResponse result = service.getSummary(PRODUCT_ID);

            assertThat(result.status()).isEqualTo(AiReviewSummaryResponse.SummaryStatus.INSUFFICIENT);
            assertThat(result.reviewCount()).isEqualTo(3L);
            assertThat(result.summary()).isNull();
        }

        @Test
        @DisplayName("요약 없음 + 리뷰 수 >= 5 → PENDING 반환")
        void getSummary_returns_pending_when_review_count_sufficient_but_no_summary() {
            given(summaryRepository.findByProduct_Id(PRODUCT_ID)).willReturn(Optional.empty());
            given(reviewRepository.countByProduct_Id(PRODUCT_ID)).willReturn(8L);
            given(reviewRepository.findAverageRatingByProductId(PRODUCT_ID)).willReturn(4.2);

            AiReviewSummaryResponse result = service.getSummary(PRODUCT_ID);

            assertThat(result.status()).isEqualTo(AiReviewSummaryResponse.SummaryStatus.PENDING);
            assertThat(result.reviewCount()).isEqualTo(8L);
            assertThat(result.summary()).isNull();
        }
    }
}
