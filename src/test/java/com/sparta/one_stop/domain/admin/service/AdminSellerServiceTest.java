package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.domain.coupon.service.CouponCommandService;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderCancelHistory;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderCancelHistoryRepository;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.point.service.PointService;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.service.UserStatusCacheService;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminSellerService 테스트")
class AdminSellerServiceTest {

    private static final Long ACTOR_ID = 1L;
    private static final Long SELLER_ID = 10L;
    @Mock private SellerRepository sellerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AdminActionHistoryRepository adminActionHistoryRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderCancelHistoryRepository orderCancelHistoryRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private UserStatusCacheService userStatusCacheService;
    @Mock private PointService pointService;
    @Mock private CouponCommandService couponCommandService;
    @InjectMocks
    private AdminSellerService adminSellerService;
    private User user;
    private Seller seller;

    @BeforeEach
    void setUp() {
        user = User.builder()
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
    }

    // ─────────────────────────────────────────────────────────────
    //  판매자 승인
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approveSeller()")
    class ApproveSeller {

        @Test
        @DisplayName("PENDING 판매자 승인 → APPROVED 상태로 변경되고 이력이 저장된다")
        void approve_pending_seller_success() {
            given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));

            adminSellerService.approveSeller(SELLER_ID, ACTOR_ID);

            assertThat(seller.getStatus()).isEqualTo(SellerStatus.APPROVED);
            verify(adminActionHistoryRepository).save(any());
        }

        @Test
        @DisplayName("이미 APPROVED 판매자 승인 → ADMIN_002 예외")
        void approve_already_approved_throws() {
            seller.approve();
            given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));

            assertThatThrownBy(() -> adminSellerService.approveSeller(SELLER_ID, ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_002));
        }

        @Test
        @DisplayName("존재하지 않는 판매자 승인 → SELLER_001 예외")
        void approve_not_found_throws() {
            given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> adminSellerService.approveSeller(SELLER_ID, ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.SELLER_001));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  판매자 반려
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectSeller()")
    class RejectSeller {

        @Test
        @DisplayName("PENDING 판매자 반려 → REJECTED 상태로 변경되고 이력이 저장된다")
        void reject_pending_seller_success() {
            given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));

            adminSellerService.rejectSeller(SELLER_ID, ACTOR_ID, "서류 미비");

            assertThat(seller.getStatus()).isEqualTo(SellerStatus.REJECTED);
            verify(adminActionHistoryRepository).save(any());
        }

        @Test
        @DisplayName("이미 REJECTED 판매자 반려 → ADMIN_003 예외")
        void reject_already_rejected_throws() {
            seller.reject();
            given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));

            assertThatThrownBy(() -> adminSellerService.rejectSeller(SELLER_ID, ACTOR_ID, "사유"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_003));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  판매자 정지
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("forceInactiveSeller()")
    class ForceInactiveSeller {

        @Test
        @DisplayName("APPROVED 판매자 정지 → SUSPENDED 상태로 변경되고 이력이 저장된다")
        void suspend_approved_seller_success() {
            seller.approve();
            given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));
            given(orderItemRepository.findBySellerIdAndStatusIn(any(), any())).willReturn(List.of());

            adminSellerService.forceInactiveSeller(SELLER_ID, ACTOR_ID, "정책 위반");

            assertThat(seller.getStatus()).isEqualTo(SellerStatus.SUSPENDED);
            verify(adminActionHistoryRepository).save(any());
        }

        @Test
        @DisplayName("판매자 상품 취소로 주문 전체가 취소되면 포인트와 쿠폰을 한 번 복구한다")
        void suspend_restores_order_benefits_when_order_becomes_fully_cancelled() {
            seller.approve();

            Order order = mock(Order.class);
            OrderItem orderItem = mock(OrderItem.class);
            ProductItem productItem = mock(ProductItem.class);

            given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));
            given(order.getId()).willReturn(100L);
            given(order.getStatus()).willReturn(OrderStatus.PAID);
            given(orderItem.getOrder()).willReturn(order);
            given(orderItem.getProductItem()).willReturn(productItem);
            given(orderItem.getQuantity()).willReturn(2);
            given(orderItem.getPrice()).willReturn(1_000L);
            given(orderItem.getStatus()).willReturn(OrderItemStatus.CANCELLED);
            given(orderItemRepository.findBySellerIdAndStatusIn(anyLong(), any()))
                .willReturn(List.of(orderItem));
            given(orderItemRepository.findAllByOrderId(100L))
                .willReturn(List.of(orderItem));
            given(pointService.refundPointByOrder(order)).willReturn(300);

            adminSellerService.forceInactiveSeller(SELLER_ID, ACTOR_ID, "정책 위반");

            verify(productItem).increaseStock(2);
            verify(orderItem).cancel();
            verify(couponCommandService).restoreCouponByOrder(order);
            verify(order).cancel();
            verify(pointService).refundPointByOrder(order);

            ArgumentCaptor<OrderCancelHistory> historyCaptor =
                ArgumentCaptor.forClass(OrderCancelHistory.class);
            verify(orderCancelHistoryRepository).save(historyCaptor.capture());
            assertThat(historyCaptor.getValue().getRestoredPoint()).isEqualTo(300);
        }

        @Test
        @DisplayName("다른 판매자 상품이 남은 부분 취소 주문은 포인트와 쿠폰을 복구하지 않는다")
        void suspend_does_not_restore_order_benefits_when_order_is_partially_cancelled() {
            seller.approve();

            Order order = mock(Order.class);
            OrderItem sellerItem = mock(OrderItem.class);
            OrderItem otherSellerItem = mock(OrderItem.class);
            ProductItem productItem = mock(ProductItem.class);

            given(sellerRepository.findById(SELLER_ID))
                .willReturn(Optional.of(seller));

            given(order.getId()).willReturn(100L);

            // 삭제
            // given(order.getStatus()).willReturn(OrderStatus.PAID);

            given(sellerItem.getOrder()).willReturn(order);
            given(sellerItem.getProductItem()).willReturn(productItem);
            given(sellerItem.getQuantity()).willReturn(1);
            given(sellerItem.getPrice()).willReturn(1_000L);
            given(sellerItem.getStatus()).willReturn(OrderItemStatus.CANCELLED);
            given(otherSellerItem.getStatus()).willReturn(OrderItemStatus.ORDERED);

            given(orderItemRepository.findBySellerIdAndStatusIn(anyLong(), any()))
                .willReturn(List.of(sellerItem));

            given(orderItemRepository.findAllByOrderId(100L))
                .willReturn(List.of(sellerItem, otherSellerItem));

            adminSellerService.forceInactiveSeller(
                SELLER_ID,
                ACTOR_ID,
                "정책 위반"
            );

            verify(couponCommandService, never())
                .restoreCouponByOrder(any());

            verify(pointService, never())
                .refundPointByOrder(any());

            verify(order, never())
                .cancel();

            ArgumentCaptor<OrderCancelHistory> historyCaptor =
                ArgumentCaptor.forClass(OrderCancelHistory.class);

            verify(orderCancelHistoryRepository)
                .save(historyCaptor.capture());

            assertThat(historyCaptor.getValue().getRestoredPoint())
                .isZero();
        }

        @Test
        @DisplayName("이미 정지된 판매자(User.suspended) 재정지 → ADMIN_004 예외")
        void suspend_already_suspended_user_throws() {
            seller.approve();
            user.suspend();
            given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));

            assertThatThrownBy(() -> adminSellerService.forceInactiveSeller(SELLER_ID, ACTOR_ID, "사유"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_004));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  판매자 정지 해제
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reactivateSeller()")
    class ReactivateSeller {

        @Test
        @DisplayName("SUSPENDED 판매자 복구 → APPROVED 상태로 변경되고 이력이 저장된다")
        void reactivate_suspended_seller_success() {
            seller.approve();
            seller.suspend();
            user.suspend();
            given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));

            adminSellerService.reactivateSeller(SELLER_ID, ACTOR_ID);

            assertThat(seller.getStatus()).isEqualTo(SellerStatus.APPROVED);
            verify(adminActionHistoryRepository).save(any());
        }

        @Test
        @DisplayName("SUSPENDED가 아닌 판매자 복구 → ADMIN_010 예외")
        void reactivate_not_suspended_throws() {
            // PENDING 상태 (기본값)
            given(sellerRepository.findById(SELLER_ID)).willReturn(Optional.of(seller));

            assertThatThrownBy(() -> adminSellerService.reactivateSeller(SELLER_ID, ACTOR_ID))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode()).isEqualTo(ErrorCode.ADMIN_010));
        }
    }
}
