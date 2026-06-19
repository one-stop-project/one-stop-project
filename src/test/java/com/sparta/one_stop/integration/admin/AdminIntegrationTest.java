package com.sparta.one_stop.integration.admin;

import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.domain.admin.service.AdminActionHistoryService;
import com.sparta.one_stop.domain.admin.service.AdminProductService;
import com.sparta.one_stop.domain.admin.service.AdminSellerService;
import com.sparta.one_stop.domain.admin.service.AdminUserService;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.integration.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@DisplayName("관리자 도메인 통합 테스트")
class AdminIntegrationTest extends IntegrationTestSupport {

    private static final Long ACTOR_ID = 1L;
    @Autowired private AdminSellerService adminSellerService;
    @Autowired private AdminProductService adminProductService;
    @Autowired private AdminUserService adminUserService;
    @Autowired private AdminActionHistoryService adminActionHistoryService;
    @Autowired private AdminActionHistoryRepository adminActionHistoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private ProductRepository productRepository;
    private User adminUser;
    private User sellerUser;
    private Seller seller;
    private Product product;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.save(User.builder()
            .email("admin@test.com")
            .password("pass")
            .name("관리자")
            .role(UserRole.ADMIN)
            .build());

        sellerUser = userRepository.save(User.builder()
            .email("seller@test.com")
            .password("pass")
            .name("판매자")
            .role(UserRole.SELLER)
            .build());

        seller = sellerRepository.save(Seller.builder()
            .user(sellerUser)
            .shopName("테스트 상점")
            .businessNumber("123-45-67890")
            .build());

        product = productRepository.save(Product.builder()
            .seller(seller)
            .name("테스트 상품")
            .description("상품 설명")
            .build());
    }

    // ─────────────────────────────────────────────────────────────
    //  ADM002 ~ ADM003 — 상품 반려
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ADM002~003 — 상품 반려")
    class ProductReject {

        @Test
        @DisplayName("ADM002 — APPROVE_REQUESTED 상태 상품 반려 → REJECTED")
        void adm002_reject_approve_requested_product() {
            adminProductService.rejectProduct(product.getId(), adminUser.getId(), "이미지 불량");

            Product updated = productRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ProductStatus.REJECTED);
        }

        @Test
        @DisplayName("ADM003 — APPROVE_REQUESTED 아닌 상태 반려 → ADMIN_017")
        void adm003_reject_non_requested_product_throws() {
            product.approve();
            productRepository.save(product);

            assertThatThrownBy(() ->
                adminProductService.rejectProduct(product.getId(), adminUser.getId(), "사유"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADMIN_017));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  ADM004 ~ ADM005 — 판매자 승인/반려
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ADM004~005 — 판매자 승인/반려")
    class SellerApproveReject {

        @Test
        @DisplayName("ADM004 — PENDING 판매자 승인 → APPROVED")
        void adm004_approve_pending_seller() {
            adminSellerService.approveSeller(seller.getId(), adminUser.getId());

            Seller updated = sellerRepository.findById(seller.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SellerStatus.APPROVED);
        }

        @Test
        @DisplayName("ADM005 — PENDING 판매자 반려 → REJECTED")
        void adm005_reject_pending_seller() {
            adminSellerService.rejectSeller(seller.getId(), adminUser.getId(), "서류 미비");

            Seller updated = sellerRepository.findById(seller.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SellerStatus.REJECTED);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  ADM006 ~ ADM007 — 판매자 정지/복구
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ADM006~007 — 판매자 정지/복구")
    class SellerSuspendReactivate {

        @Test
        @DisplayName("ADM006 — APPROVED 판매자 정지 → SUSPENDED")
        void adm006_suspend_approved_seller() {
            adminSellerService.approveSeller(seller.getId(), adminUser.getId());

            adminSellerService.forceInactiveSeller(seller.getId(), adminUser.getId(), "정책 위반");

            Seller updated = sellerRepository.findById(seller.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
        }

        @Test
        @DisplayName("ADM007 — SUSPENDED 판매자 복구 → APPROVED")
        void adm007_reactivate_suspended_seller() {
            adminSellerService.approveSeller(seller.getId(), adminUser.getId());
            adminSellerService.forceInactiveSeller(seller.getId(), adminUser.getId(), "정책 위반");

            adminSellerService.reactivateSeller(seller.getId(), adminUser.getId());

            Seller updated = sellerRepository.findById(seller.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(SellerStatus.APPROVED);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  ADM012 ~ ADM013 — 처리 이력 조회
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ADM012~013 — 처리 이력 조회")
    class ActionHistory {

        @Test
        @DisplayName("ADM012 — ADMIN은 본인 처리 이력만 조회")
        void adm012_admin_sees_own_history() {
            adminSellerService.approveSeller(seller.getId(), adminUser.getId());

            var result = adminActionHistoryService.getHistories(
                adminUser.getId(), UserRole.ADMIN, PageRequest.of(0, 10));

            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent())
                .allMatch(h -> h.actorId().equals(adminUser.getId()));
        }

        @Test
        @DisplayName("ADM013 — SUPER_ADMIN은 전체 처리 이력 조회")
        void adm013_super_admin_sees_all_history() {
            adminSellerService.approveSeller(seller.getId(), adminUser.getId());

            var result = adminActionHistoryService.getHistories(
                adminUser.getId(), UserRole.SUPER_ADMIN, PageRequest.of(0, 10));

            assertThat(result).isNotNull();
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  ADM014 ~ ADM015 — 관리자 권한 부여/회수
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ADM014~015 — 관리자 권한 부여/회수")
    class AdminRoleManagement {

        @Test
        @DisplayName("ADM014 — BUYER에게 관리자 권한 부여 → ADMIN")
        void adm014_grant_admin_to_buyer() {
            User buyer = userRepository.save(User.builder()
                .email("buyer@test.com")
                .password("pass")
                .name("구매자")
                .role(UserRole.BUYER)
                .build());

            adminUserService.grantAdmin(buyer.getId(), adminUser.getId());

            User updated = userRepository.findById(buyer.getId()).orElseThrow();
            assertThat(updated.getRole()).isEqualTo(UserRole.ADMIN);
        }

        @Test
        @DisplayName("ADM015 — ADMIN 권한 회수 → BUYER")
        void adm015_revoke_admin_from_admin() {
            User targetAdmin = userRepository.save(User.builder()
                .email("target-admin@test.com")
                .password("pass")
                .name("대상관리자")
                .role(UserRole.ADMIN)
                .build());

            adminUserService.revokeAdmin(targetAdmin.getId(), adminUser.getId());

            User updated = userRepository.findById(targetAdmin.getId()).orElseThrow();
            assertThat(updated.getRole()).isEqualTo(UserRole.BUYER);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  ADM016 ~ ADM019 — 선행 상태 검증 실패
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ADM016~019 — 선행 상태 검증 실패")
    class StatusValidationFailures {

        @Test
        @DisplayName("ADM016 — PENDING 아닌 판매자 승인 시도 → ADMIN_018")
        void adm016_approve_non_pending_seller_throws() {
            adminSellerService.approveSeller(seller.getId(), adminUser.getId());

            assertThatThrownBy(() ->
                adminSellerService.approveSeller(seller.getId(), adminUser.getId()))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADMIN_018));
        }

        @Test
        @DisplayName("ADM017 — PENDING 아닌 판매자 반려 시도 → ADMIN_018")
        void adm017_reject_non_pending_seller_throws() {
            adminSellerService.rejectSeller(seller.getId(), adminUser.getId(), "서류 미비");

            assertThatThrownBy(() ->
                adminSellerService.rejectSeller(seller.getId(), adminUser.getId(), "사유"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADMIN_018));
        }

        @Test
        @DisplayName("ADM018 — 이미 SUSPENDED 판매자 정지 시도 → ADMIN_019")
        void adm018_suspend_already_suspended_seller_throws() {
            adminSellerService.approveSeller(seller.getId(), adminUser.getId());
            adminSellerService.forceInactiveSeller(seller.getId(), adminUser.getId(), "정책 위반");

            assertThatThrownBy(() ->
                adminSellerService.forceInactiveSeller(seller.getId(), adminUser.getId(), "사유"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADMIN_019));
        }

        @Test
        @DisplayName("ADM019 — SUSPENDED 아닌 판매자 복구 시도 → ADMIN_010")
        void adm019_reactivate_non_suspended_seller_throws() {
            assertThatThrownBy(() ->
                adminSellerService.reactivateSeller(seller.getId(), adminUser.getId()))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ADMIN_010));
        }
    }
}
