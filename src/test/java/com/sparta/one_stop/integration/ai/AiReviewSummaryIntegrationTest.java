package com.sparta.one_stop.integration.ai;

import com.sparta.one_stop.domain.ai.dto.AiReviewSummaryResponse;
import com.sparta.one_stop.domain.ai.service.AiReviewSummaryService;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.integration.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AiReviewSummaryService 통합 테스트")
class AiReviewSummaryIntegrationTest extends IntegrationTestSupport {

    @Autowired private AiReviewSummaryService aiReviewSummaryService;
    @Autowired private UserRepository userRepository;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private ProductRepository productRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
            .email("seller@test.com")
            .password("pass")
            .name("판매자")
            .role(UserRole.SELLER)
            .build());

        Seller seller = sellerRepository.save(Seller.builder()
            .user(user)
            .shopName("테스트 상점")
            .businessNumber("123-45-67890")
            .build());

        product = productRepository.save(Product.builder()
            .seller(seller)
            .name("테스트 상품")
            .description("상품 설명")
            .build());
    }

    @Nested
    @DisplayName("refreshSummary() 실패 케이스")
    class RefreshSummaryFailure {

        @Test
        @DisplayName("존재하지 않는 상품 → PRODUCT_001 예외")
        void refresh_summary_throws_when_product_not_found() {
            assertThatThrownBy(() -> aiReviewSummaryService.refreshSummary(999999L))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_001));
        }

        @Test
        @DisplayName("리뷰 수 < 5 → INSUFFICIENT 반환 (AI 호출 없음)")
        void refresh_summary_returns_insufficient_when_review_count_below_threshold() {
            // 리뷰 없는 상태 (기본 0건)
            AiReviewSummaryResponse result = aiReviewSummaryService.refreshSummary(product.getId());

            assertThat(result.status()).isEqualTo(AiReviewSummaryResponse.SummaryStatus.INSUFFICIENT);
            assertThat(result.reviewCount()).isEqualTo(0L);
        }
    }
}
