package com.sparta.one_stop.domain.user.entity;

import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seller 엔티티 상태 전이 테스트
 *
 * <p><b>검증 목적</b>
 * <ul>
 *   <li>판매자 가입 → 승인 → 운영 → 정지 → 재활성화 라이프사이클 검증</li>
 *   <li>잘못된 상태 전이 차단 (예: PENDING 상태에서 직접 SUSPENDED 시도)</li>
 *   <li>초기 생성 시 상태가 PENDING으로 정확히 세팅되는지</li>
 * </ul>
 *
 * <p><b>테스트 전략</b>: 순수 단위 테스트 (Mock 불필요 — 도메인 객체 자체 검증)
 */
@DisplayName("Seller 엔티티 상태 전이 테스트")
class SellerTest {

    private Seller seller;
    private User user;

    @BeforeEach
    void setUp() {
        // User는 Mock이 아닌 실제 객체 (Seller에서 user.getId() 등을 호출하지 않으므로)
        user = User.builder()
            .email("seller@example.com")
            .password("encoded")
            .name("판매자")
            .phone("010-1234-5678")
            .build();

        seller = Seller.builder()
            .user(user)
            .shopName("테스트샵")
            .businessNumber("123-45-67890")
            .bankAccount("1234567890")
            .build();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  생성
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Nested
    @DisplayName("판매자 생성")
    class Creation {

        @Test
        @DisplayName("판매자 가입 시 초기 상태는 PENDING(승인대기)")
        void initial_status_is_pending() {
            assertThat(seller.getStatus()).isEqualTo(SellerStatus.PENDING);
            assertThat(seller.isPending()).isTrue();
            assertThat(seller.isApproved()).isFalse();
        }

        @Test
        @DisplayName("필수 정보가 정확히 세팅된다")
        void required_fields_are_set() {
            assertThat(seller.getShopName()).isEqualTo("테스트샵");
            assertThat(seller.getBusinessNumber()).isEqualTo("123-45-67890");
            assertThat(seller.getBankAccount()).isEqualTo("1234567890");
            assertThat(seller.getUser()).isEqualTo(user);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  승인/반려 (PENDING → APPROVED / REJECTED)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Nested
    @DisplayName("승인/반려 전이")
    class ApprovalTransitions {

        @Test
        @DisplayName("승인 시 상태가 APPROVED로 전환된다")
        void approve_changes_status_to_approved() {
            seller.approve();

            assertThat(seller.getStatus()).isEqualTo(SellerStatus.APPROVED);
            assertThat(seller.isApproved()).isTrue();
            assertThat(seller.isPending()).isFalse();
        }

        @Test
        @DisplayName("반려 시 상태가 REJECTED로 전환된다")
        void reject_changes_status_to_rejected() {
            seller.reject();

            assertThat(seller.getStatus()).isEqualTo(SellerStatus.REJECTED);
            assertThat(seller.isApproved()).isFalse();
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  정지/재활성화 (핵심: 가드 조건이 잘 동작하는가)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Nested
    @DisplayName("정지/재활성화 전이")
    class SuspensionTransitions {

        @Test
        @DisplayName("APPROVED 상태에서만 정지 가능")
        void can_suspend_only_when_approved() {
            // given
            seller.approve();

            // when
            seller.suspend();

            // then
            assertThat(seller.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
        }

        @Test
        @DisplayName("PENDING 상태에서 정지 시도 시 예외 — 비즈니스 규칙 보호")
        void cannot_suspend_pending_seller() {
            // PENDING 상태 (초기 상태)
            assertThatThrownBy(() -> seller.suspend())
                .isInstanceOf(CustomException.class);

            assertThat(seller.getStatus()).isEqualTo(SellerStatus.PENDING);
        }

        @Test
        @DisplayName("REJECTED 상태에서 정지 시도 시 예외")
        void cannot_suspend_rejected_seller() {
            seller.reject();

            assertThatThrownBy(() -> seller.suspend())
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("이미 SUSPENDED 상태인 판매자를 다시 정지 시도 시 예외")
        void cannot_suspend_already_suspended_seller() {
            seller.approve();
            seller.suspend();

            assertThatThrownBy(() -> seller.suspend())
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("SUSPENDED 상태에서만 재활성화 가능 → APPROVED로 복귀")
        void can_reactivate_only_when_suspended() {
            // given
            seller.approve();
            seller.suspend();

            // when
            seller.reactivate();

            // then
            assertThat(seller.getStatus()).isEqualTo(SellerStatus.APPROVED);
            assertThat(seller.isApproved()).isTrue();
        }

        @Test
        @DisplayName("APPROVED 상태에서 재활성화 시도 시 예외 — 의미 없는 호출 방지")
        void cannot_reactivate_approved_seller() {
            seller.approve();

            assertThatThrownBy(() -> seller.reactivate())
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("PENDING 상태에서 재활성화 시도 시 예외")
        void cannot_reactivate_pending_seller() {
            assertThatThrownBy(() -> seller.reactivate())
                .isInstanceOf(CustomException.class);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  라이프사이클 통합 (시나리오 기반)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Nested
    @DisplayName("판매자 라이프사이클")
    class Lifecycle {

        @Test
        @DisplayName("정상 시나리오: 가입 → 승인 → 정지 → 재활성화")
        void full_lifecycle_scenario() {
            // 1) 가입
            assertThat(seller.isPending()).isTrue();

            // 2) 승인
            seller.approve();
            assertThat(seller.isApproved()).isTrue();

            // 3) 부적절 사유로 정지
            seller.suspend();
            assertThat(seller.getStatus()).isEqualTo(SellerStatus.SUSPENDED);

            // 4) 소명 후 재활성화
            seller.reactivate();
            assertThat(seller.isApproved()).isTrue();
        }

        @Test
        @DisplayName("반려 시나리오: 가입 → 반려 (이후 정지/재활성화 불가)")
        void rejection_is_terminal_for_suspend() {
            seller.reject();

            assertThatThrownBy(() -> seller.suspend()).isInstanceOf(CustomException.class);
            assertThatThrownBy(() -> seller.reactivate()).isInstanceOf(CustomException.class);
        }
    }
}
