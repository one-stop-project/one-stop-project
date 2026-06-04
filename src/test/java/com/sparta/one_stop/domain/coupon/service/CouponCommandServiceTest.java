package com.sparta.one_stop.domain.coupon.service;

import com.sparta.one_stop.domain.coupon.dto.CouponRestoreResult;
import com.sparta.one_stop.domain.coupon.dto.response.IssueCouponResponse;
import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.coupon.service.issue.CouponIssueStrategy;
import com.sparta.one_stop.domain.coupon.service.issue.CouponIssueStrategyProvider;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponCommandServiceTest {

    @Mock
    private CouponIssueStrategyProvider couponIssueStrategyProvider;

    @Mock
    private CouponIssueStrategy couponIssueStrategy;

    @InjectMocks
    private CouponCommandService couponCommandService;

    @Test
    @DisplayName("issueCoupon 성공 - 설정된 쿠폰 발급 전략에 발급 처리를 위임한다")
    void issueCoupon_success_delegateToStrategy() {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        IssueCouponResponse response = new IssueCouponResponse(
            100L,
            couponId,
            "테스트 쿠폰",
            UserCouponStatus.AVAILABLE,
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(30)
        );

        when(couponIssueStrategyProvider.getStrategy())
            .thenReturn(couponIssueStrategy);
        when(couponIssueStrategy.issue(userId, couponId))
            .thenReturn(response);

        // when
        IssueCouponResponse result = couponCommandService.issueCoupon(
            userId,
            couponId
        );

        // then
        assertThat(result).isSameAs(response);

        verify(couponIssueStrategyProvider).getStrategy();
        verify(couponIssueStrategy).issue(userId, couponId);
    }

    @Test
    @DisplayName("issueCoupon 실패 - 전략에서 예외가 발생하면 그대로 전파한다")
    void issueCoupon_fail_whenStrategyThrowsException() {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        when(couponIssueStrategyProvider.getStrategy())
            .thenReturn(couponIssueStrategy);
        when(couponIssueStrategy.issue(userId, couponId))
            .thenThrow(CustomException.class);

        // when & then
        assertThatThrownBy(() -> couponCommandService.issueCoupon(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(couponIssueStrategyProvider).getStrategy();
        verify(couponIssueStrategy).issue(userId, couponId);
    }

    @Test
    @DisplayName("useCouponByOrder 성공 - 주문에 적용된 쿠폰을 USED 처리한다")
    void useCouponByOrder_success() {
        // given
        Long userId = 1L;

        Order order = mock(Order.class);
        User user = mock(User.class);
        UserCoupon userCoupon = mock(UserCoupon.class);

        when(order.getUserCoupon())
            .thenReturn(userCoupon);
        when(order.getUser())
            .thenReturn(user);
        when(user.getId())
            .thenReturn(userId);

        // when
        couponCommandService.useCouponByOrder(order);

        // then
        InOrder inOrder = inOrder(userCoupon);
        inOrder.verify(userCoupon).validateUsable(
            eq(userId),
            any(LocalDateTime.class)
        );
        inOrder.verify(userCoupon).use(
            eq(order),
            any(LocalDateTime.class)
        );
    }

    @Test
    @DisplayName("useCouponByOrder 성공 - 주문에 쿠폰이 없으면 아무 처리도 하지 않는다")
    void useCouponByOrder_success_whenOrderHasNoCoupon() {
        // given
        Order order = mock(Order.class);

        when(order.getUserCoupon())
            .thenReturn(null);

        // when
        couponCommandService.useCouponByOrder(order);

        // then
        verify(order).getUserCoupon();
        verify(order, never()).getUser();
    }

    @Test
    @DisplayName("useCouponByOrder 실패 - 주문 정보가 없으면 예외가 발생한다")
    void useCouponByOrder_fail_whenOrderIsNull() {
        // when & then
        assertThatThrownBy(() -> couponCommandService.useCouponByOrder(null))
            .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("restoreCouponByOrder 성공 - 결제 완료 주문의 사용 쿠폰을 복구하고 결과를 반환한다")
    void restoreCouponByOrder_success() {
        // given
        Order order = mock(Order.class);
        UserCoupon userCoupon = mock(UserCoupon.class);
        Coupon coupon = mock(Coupon.class);

        when(order.getUserCoupon())
            .thenReturn(userCoupon);
        when(order.getStatus())
            .thenReturn(OrderStatus.PAID);

        when(userCoupon.getId())
            .thenReturn(100L);
        when(userCoupon.getCoupon())
            .thenReturn(coupon);
        when(coupon.getName())
            .thenReturn("테스트 쿠폰");

        AtomicReference<UserCouponStatus> statusRef =
            new AtomicReference<>(UserCouponStatus.USED);

        when(userCoupon.getStatus())
            .thenAnswer(invocation -> statusRef.get());

        doAnswer(invocation -> {
            statusRef.set(UserCouponStatus.AVAILABLE);
            return null;
        }).when(userCoupon).restore(any(LocalDateTime.class));

        // when
        CouponRestoreResult result = couponCommandService.restoreCouponByOrder(order);

        // then
        assertThat(result).isNotNull();
        assertThat(result.userCouponId()).isEqualTo(100L);
        assertThat(result.couponName()).isEqualTo("테스트 쿠폰");
        assertThat(result.status()).isEqualTo(UserCouponStatus.AVAILABLE);

        verify(order).getUserCoupon();
        verify(order).getStatus();
        verify(userCoupon).restore(any(LocalDateTime.class));
        verify(userCoupon).getId();
        verify(userCoupon).getCoupon();
    }

    @Test
    @DisplayName("restoreCouponByOrder 성공 - 결제 전 주문이면 쿠폰 복구를 하지 않는다")
    void restoreCouponByOrder_success_whenPendingPaymentOrder() {
        // given
        Order order = mock(Order.class);
        UserCoupon userCoupon = mock(UserCoupon.class);

        when(order.getUserCoupon())
            .thenReturn(userCoupon);
        when(order.getStatus())
            .thenReturn(OrderStatus.PENDING_PAYMENT);

        // when
        CouponRestoreResult result = couponCommandService.restoreCouponByOrder(order);

        // then
        assertThat(result).isNull();
        verify(userCoupon, never()).restore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("restoreCouponByOrder 성공 - 주문에 쿠폰이 없으면 null을 반환한다")
    void restoreCouponByOrder_success_whenOrderHasNoCoupon() {
        // given
        Order order = mock(Order.class);

        when(order.getUserCoupon())
            .thenReturn(null);

        // when
        CouponRestoreResult result = couponCommandService.restoreCouponByOrder(order);

        // then
        assertThat(result).isNull();
        verify(order).getUserCoupon();
        verify(order, never()).getStatus();
    }

    @Test
    @DisplayName("restoreCouponByOrder 실패 - 주문 정보가 없으면 예외가 발생한다")
    void restoreCouponByOrder_fail_whenOrderIsNull() {
        // when & then
        assertThatThrownBy(() -> couponCommandService.restoreCouponByOrder(null))
            .isInstanceOf(CustomException.class);
    }

}
