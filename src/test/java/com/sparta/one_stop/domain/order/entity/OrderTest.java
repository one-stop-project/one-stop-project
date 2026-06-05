package com.sparta.one_stop.domain.order.entity;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.order.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OrderTest {

    @Test
    @DisplayName("Order 생성 성공 - 초기 상태는 PENDING_PAYMENT")
    void createOrder_success() {
        // given
        User user = mock(User.class);

        // when
        Order order = new Order(
            user,
            null,
            10000L,
            0L,
            13000L,
            0,
            null,
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞에 놓아주세요",
            3000L,
            OrderType.CART
        );

        // then
        assertThat(order.getUser()).isSameAs(user);
        assertThat(order.getTotalPrice()).isEqualTo(10000L);
        assertThat(order.getDiscountPrice()).isEqualTo(0L);
        assertThat(order.getFinalPrice()).isEqualTo(13000L);
        assertThat(order.getUsedPoint()).isEqualTo(0);
        assertThat(order.getReceiverName()).isEqualTo("홍길동");
        assertThat(order.getReceiverPhone()).isEqualTo("010-1234-5678");
        assertThat(order.getReceiverAddress()).isEqualTo("서울시 강남구");
        assertThat(order.getDeliveryMessage()).isEqualTo("문 앞에 놓아주세요");
        assertThat(order.getDeliveryFee()).isEqualTo(3000L);
        assertThat(order.getOrderType()).isEqualTo(OrderType.CART);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("completePayment 성공 - PENDING_PAYMENT 상태를 PAID로 변경한다")
    void completePayment_success() {
        // given
        Order order = order();

        // when
        order.completePayment();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("completePayment 실패 - PENDING_PAYMENT 상태가 아니면 결제 완료 처리할 수 없다")
    void completePayment_fail_whenStatusIsNotPendingPayment() {
        // given
        Order order = order();
        order.completePayment();

        // when & then
        assertThatThrownBy(order::completePayment)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("결제 대기 상태에서만 결제 완료 처리할 수 있습니다.");
    }

    @Test
    @DisplayName("cancel 성공 - 주문 상태를 CANCELLED로 변경한다")
    void cancel_success() {
        // given
        Order order = order();

        // when
        order.cancel();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel 성공 - PAID 상태 주문도 CANCELLED로 변경한다")
    void cancel_success_whenStatusIsPaid() {
        // given
        Order order = order();
        order.completePayment();

        // when
        order.cancel();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel 실패 - 이미 취소된 주문은 다시 취소할 수 없다")
    void cancel_fail_whenAlreadyCancelled() {
        // given
        Order order = order();
        order.cancel();

        // when & then
        assertThatThrownBy(order::cancel)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("이미 취소된 주문입니다.");
    }

    private Order order() {
        return new Order(
            mock(User.class),
            null,
            10000L,
            0L,
            13000L,
            0,
            null,
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞에 놔주세요",
            3000L,
            OrderType.CART
        );
    }

}
