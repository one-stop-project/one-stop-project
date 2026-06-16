package com.sparta.one_stop.domain.payment.entity;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.global.enums.payment.PaymentMethod;
import com.sparta.one_stop.global.enums.payment.PaymentStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new Payment(
                null,
                "payment-key",
                10000L,
                PaymentMethod.MOCK
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_020);
    }

    @Test
    @DisplayName("Payment 생성 실패 - 결제 키가 null이면 예외 발생")
    void createPayment_fail_whenPaymentKeyIsNull() {
        // given
        Order order = order();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new Payment(
                order,
                null,
                10000L,
                PaymentMethod.MOCK
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_021);
    }

    @Test
    @DisplayName("Payment 생성 실패 - 결제 키가 blank이면 예외 발생")
    void createPayment_fail_whenPaymentKeyIsBlank() {
        // given
        Order order = order();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new Payment(
                order,
                " ",
                10000L,
                PaymentMethod.MOCK
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_021);
    }

    @Test
    @DisplayName("Payment 생성 실패 - 결제 금액이 null이면 예외 발생")
    void createPayment_fail_whenAmountIsNull() {
        // given
        Order order = order();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new Payment(
                order,
                "payment-key",
                null,
                PaymentMethod.MOCK
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_022);
    }

    @Test
    @DisplayName("Payment 생성 실패 - 결제 금액이 음수이면 예외 발생")
    void createPayment_fail_whenAmountIsNegative() {
        // given
        Order order = order();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new Payment(
                order,
                "payment-key",
                -1L,
                PaymentMethod.MOCK
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_022);
    }

    @Test
    @DisplayName("Payment 생성 실패 - 결제 수단이 null이면 예외 발생")
    void createPayment_fail_whenMethodIsNull() {
        // given
        Order order = order();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new Payment(
                order,
                "payment-key",
                10000L,
                null
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_023);
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

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            payment::approve
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_024);
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

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            payment::fail
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_025);
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

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            payment::cancel
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_027);
    }

    @Test
    @DisplayName("cancel 실패 - 이미 취소된 결제는 다시 취소할 수 없다")
    void cancel_fail_whenAlreadyCancelled() {
        // given
        Payment payment = payment();
        payment.approve();
        payment.cancel();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            payment::cancel
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_026);
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
