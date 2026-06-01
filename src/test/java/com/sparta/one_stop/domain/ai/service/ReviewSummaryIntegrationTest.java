package com.sparta.one_stop.domain.ai.service;

import com.sparta.one_stop.domain.ai.dto.ReviewCategoryType;
import com.sparta.one_stop.domain.ai.dto.ReviewSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Gemini API 를 호출하는 통합 테스트.
 * local 프로파일의 API Key 를 사용하므로 네트워크 연결이 필요합니다.
 */
@SpringBootTest
@ActiveProfiles("local")
class ReviewSummaryIntegrationTest {

    @Autowired
    private ReviewSummaryService reviewSummaryService;

    @Test
    @DisplayName("의류 리뷰 요약 – 실제 Gemini API 호출")
    void clothing_review_summary_with_real_api() {
        String reviews = """
                1. 착용감이 정말 편해요. 소재가 부드럽고 사이즈도 딱 맞습니다.
                2. 기대했던 것보다 소재가 얇은 느낌이에요. 사이즈는 정확해요.
                3. 색상이 사진과 달라서 조금 아쉬웠지만 착용감은 최고입니다.
                """;

        ReviewSummary result = reviewSummaryService.summarize(ReviewCategoryType.CLOTHING, reviews);

        System.out.println("=== AI 리뷰 요약 결과 ===");
        System.out.println("장점: " + result.pros());
        System.out.println("단점: " + result.cons());
        System.out.println("키워드: " + result.keywords());
        System.out.println("감성: " + result.sentiment());

        assertThat(result).isNotNull();
        assertThat(result.sentiment()).isNotEqualTo("UNAVAILABLE");
        assertThat(result.pros()).isNotEmpty();
        assertThat(result.keywords()).isNotEmpty();
    }
}
