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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
@Tag("integration")
@DisplayName("AiReviewSummaryService 통합 테스트")
class AiReviewSummaryIntegrationTest extends IntegrationTestSupport {

    @Autowired private AiReviewSummaryService aiReviewSummaryService;
    @Autowired private UserRepository userRepository;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private ProductRepository productRepository;

    private Long productId;
    private Long sellerId;
    private Long userId;

    // refreshSummary()는 NOT_SUPPORTED 전파 방식이라 외부 테스트 트랜잭션을 중단하고
    // 새 트랜잭션을 연다. @BeforeEach는 테스트 트랜잭션 안에서 실행되므로 미커밋 상태.
    // @BeforeTransaction으로 테스트 트랜잭션 시작 전에 데이터를 커밋해야 읽을 수 있다.
    @BeforeTransaction
    void setUpData() {
        User user = userRepository.save(User.builder()
            .email("ai-seller@test.com")
            .password("pass")
            .name("판매자")
            .role(UserRole.SELLER)
            .build());
        userId = user.getId();

        Seller seller = sellerRepository.save(Seller.builder()
            .user(user)
            .shopName("AI 테스트 상점")
            .businessNumber("111-22-33333")
            .build());
        sellerId = seller.getId();

        Product product = productRepository.save(Product.builder()
            .seller(seller)
            .name("AI 테스트 상품")
            .description("상품 설명")
            .build());
        productId = product.getId();
    }

    @AfterTransaction
    void tearDownData() {
        productRepository.findById(productId).ifPresent(productRepository::delete);
        sellerRepository.findById(sellerId).ifPresent(sellerRepository::delete);
        userRepository.findById(userId).ifPresent(userRepository::delete);
    }

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
        AiReviewSummaryResponse result = aiReviewSummaryService.refreshSummary(productId);

        assertThat(result.status()).isEqualTo(AiReviewSummaryResponse.SummaryStatus.INSUFFICIENT);
        assertThat(result.reviewCount()).isEqualTo(0L);
    }
}
