package com.sparta.one_stop.domain.payment.entity;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.global.enums.payment.PaymentMethod;
import com.sparta.one_stop.global.enums.payment.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    @DisplayName("Payment 생성 성공")
    void createPayment_success() {
        // given
        Order order = order();

        // when
        Payment payment = new Payment(
            order,
            "payment-key",
            10000L,
            PaymentMethod.MOCK
        );

        // then
        assertThat(payment.getOrder()).isSameAs(order);
        assertThat(payment.getPaymentKey()).isEqualTo("payment-key");
        assertThat(payment.getAmount()).isEqualTo(10000L);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.MOCK);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getApprovedAt()).isNull();
        assertThat(payment.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("Payment 생성 실패 - 주문 정보가 null이면 예외 발생")
    void createPayment_fail_whenOrderIsNull() {
        // when & then
        assertThatThrownBy(() -> new Payment(
            null,
            "payment-key",
            10000L,
            PaymentMethod.MOCK
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("주문 정보는 필수입니다.");
    }

    @Test
    @DisplayName("Payment 생성 실패 - 결제 키가 null이면 예외 발생")
    void createPayment_fail_whenPaymentKeyIsNull() {
        // given
        Order order = order();

        // when & then
        assertThatThrownBy(() -> new Payment(
            order,
            null,
            10000L,
            PaymentMethod.MOCK
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("결제 키는 필수입니다.");
    }

    @Test
    @DisplayName("Payment 생성 실패 - 결제 키가 blank이면 예외 발생")
    void createPayment_fail_whenPaymentKeyIsBlank() {
        // given
        Order order = order();

        // when & then
        assertThatThrownBy(() -> new Payment(
            order,
            " ",
            10000L,
            PaymentMethod.MOCK
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("결제 키는 필수입니다.");
    }

    @Test
    @DisplayName("Payment 생성 실패 - 결제 금액이 null이면 예외 발생")
    void createPayment_fail_whenAmountIsNull() {
        // given
        Order order = order();

        // when & then
        assertThatThrownBy(() -> new Payment(
            order,
            "payment-key",
            null,
            PaymentMethod.MOCK
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("결제 금액은 0원 이상이어야 합니다.");
    }

    @Test
    @DisplayName("Payment 생성 실패 - 결제 금액이 음수이면 예외 발생")
    void createPayment_fail_whenAmountIsNegative() {
        // given
        Order order = order();

        // when & then
        assertThatThrownBy(() -> new Payment(
            order,
            "payment-key",
            -1L,
            PaymentMethod.MOCK
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("결제 금액은 0원 이상이어야 합니다.");
    }

    @Test
    @DisplayName("Payment 생성 실패 - 결제 수단이 null이면 예외 발생")
    void createPayment_fail_whenMethodIsNull() {
        // given
        Order order = order();

        // when & then
        assertThatThrownBy(() -> new Payment(
            order,
            "payment-key",
            10000L,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("결제 수단은 필수입니다.");
    }

    @Test
    @DisplayName("approve 성공 - READY 상태 결제를 PAID로 변경하고 승인 시간을 기록한다")
    void approve_success() {
        // given
        Payment payment = payment();

        // when
        payment.approve();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("approve 실패 - READY 상태가 아니면 승인할 수 없다")
    void approve_fail_whenStatusIsNotReady() {
        // given
        Payment payment = payment();
        payment.approve();

        // when & then
        assertThatThrownBy(payment::approve)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("결제 대기 상태에서만 승인할 수 있습니다.");
    }

    @Test
    @DisplayName("fail 성공 - READY 상태 결제를 FAILED로 변경한다")
    void fail_success() {
        // given
        Payment payment = payment();

        // when
        payment.fail();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getApprovedAt()).isNull();
        assertThat(payment.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("fail 실패 - READY 상태가 아니면 실패 처리할 수 없다")
    void fail_fail_whenStatusIsNotReady() {
        // given
        Payment payment = payment();
        payment.approve();

        // when & then
        assertThatThrownBy(payment::fail)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("결제 대기 상태에서만 실패 처리할 수 있습니다.");
    }

    @Test
    @DisplayName("cancel 성공 - PAID 상태 결제를 CANCELLED로 변경하고 취소 시간을 기록한다")
    void cancel_success() {
        // given
        Payment payment = payment();
        payment.approve();

        // when
        payment.cancel();

        // then
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("cancel 실패 - READY 상태 결제는 취소할 수 없다")
    void cancel_fail_whenStatusIsReady() {
        // given
        Payment payment = payment();

        // when & then
        assertThatThrownBy(payment::cancel)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("결제 완료 상태에서만 취소할 수 있습니다.");
    }

    @Test
    @DisplayName("cancel 실패 - 이미 취소된 결제는 다시 취소할 수 없다")
    void cancel_fail_whenAlreadyCancelled() {
        // given
        Payment payment = payment();
        payment.approve();
        payment.cancel();

        // when & then
        assertThatThrownBy(payment::cancel)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("이미 취소된 결제입니다.");
    }

    private Payment payment() {
        return new Payment(
            order(),
            "payment-key",
            10000L,
            PaymentMethod.MOCK
        );
    }

    private Order order() {
        return org.mockito.Mockito.mock(Order.class);
    }

}
