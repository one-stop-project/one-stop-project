package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminProductService 테스트")
class AdminProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private AdminActionHistoryRepository adminActionHistoryRepository;

    @InjectMocks
    private AdminProductService adminProductService;

    private static final Long ACTOR_ID = 1L;
    private static final Long PRODUCT_ID = 100L;

    private Seller seller;
    private Product product;

    @BeforeEach
    void setUp() {
        User user = User.builder()
            .email("seller@test.com")
            .password("pass")
            .name("판매자")
            .role(UserRole.SELLER)
            .build();

        seller = Seller.builder()
            .user(user)
            .shopName("테스트 상점")
            .businessNumber("123-45-67890")
            .build();

        product = Product.builder()
            .seller(seller)
            .name("테스트 상품")
            .description("상품 설명")
            .build();
    }

    // ─────────────────────────────────────────────────────────────
    //  상품 승인
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approveProduct()")
    class ApproveProduct {

        @Test
        @DisplayName("APPROVE_REQUESTED 상품 승인 → APPROVED 상태로 변경되고 이력이 저장된다")
        void approve_requested_product_success() {
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

            adminProductService.approveProduct(PRODUCT_ID, ACTOR_ID);

            assertThat(product.getStatus()).isEqualTo(ProductStatus.APPROVED);
            verify(adminActionHistoryRepository).save(any());
        }

        @Test
        @DisplayName("이미 APPROVED 상품 승인 → ADMIN_008 예외")
        void approve_already_approved_throws() {
            product.approve();
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

            assertThatThrownBy(() -> adminProductService.approveProduct(PRODUCT_ID, ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_008));
        }

        @Test
        @DisplayName("존재하지 않는 상품 승인 → PRODUCT_001 예외")
        void approve_not_found_throws() {
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminProductService.approveProduct(PRODUCT_ID, ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.PRODUCT_001));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  상품 반려
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectProduct()")
    class RejectProduct {

        @Test
        @DisplayName("APPROVE_REQUESTED 상품 반려 → REJECTED 상태로 변경되고 이력이 저장된다")
        void reject_requested_product_success() {
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

            adminProductService.rejectProduct(PRODUCT_ID, ACTOR_ID, "이미지 불량");

            assertThat(product.getStatus()).isEqualTo(ProductStatus.REJECTED);
            verify(adminActionHistoryRepository).save(any());
        }

        @Test
        @DisplayName("이미 REJECTED 상품 반려 → ADMIN_009 예외")
        void reject_already_rejected_throws() {
            product.reject();
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

            assertThatThrownBy(() -> adminProductService.rejectProduct(PRODUCT_ID, ACTOR_ID, "사유"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_009));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  상품 강제 비활성화
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("forceInactiveProduct()")
    class ForceInactiveProduct {

        @Test
        @DisplayName("APPROVED 상품 강제 비활성화 → FORCE_INACTIVE 상태로 변경되고 이력이 저장된다")
        void force_inactive_approved_product_success() {
            product.approve();
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

            adminProductService.forceInactiveProduct(PRODUCT_ID, ACTOR_ID, "정책 위반");

            assertThat(product.getStatus()).isEqualTo(ProductStatus.FORCE_INACTIVE);
            verify(adminActionHistoryRepository).save(any());
        }

        @Test
        @DisplayName("이미 FORCE_INACTIVE 상품 재비활성화 → ADMIN_007 예외")
        void force_inactive_already_inactive_throws() {
            product.approve();
            product.forceInactive();
            given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

            assertThatThrownBy(() -> adminProductService.forceInactiveProduct(PRODUCT_ID, ACTOR_ID, "사유"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_007));
        }
    }
}
