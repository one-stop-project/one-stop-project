package com.sparta.one_stop.domain.order.entity;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.order.OrderType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertThat(order.getUserCoupon()).isNull();
        assertThat(order.getTotalPrice()).isEqualTo(10000L);
        assertThat(order.getDiscountPrice()).isEqualTo(0L);
        assertThat(order.getFinalPrice()).isEqualTo(13000L);
        assertThat(order.getUsedPoint()).isEqualTo(0);
        assertThat(order.getSubscriptionDiscount()).isEqualTo(0L);
        assertThat(order.getReceiverName()).isEqualTo("홍길동");
        assertThat(order.getReceiverPhone()).isEqualTo("010-1234-5678");
        assertThat(order.getReceiverAddress()).isEqualTo("서울시 강남구");
        assertThat(order.getDeliveryMessage()).isEqualTo("문 앞에 놓아주세요");
        assertThat(order.getDeliveryFee()).isEqualTo(3000L);
        assertThat(order.getOrderType()).isEqualTo(OrderType.CART);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("Order 생성 성공 - subscriptionDiscount가 있으면 해당 값으로 설정된다")
    void createOrder_success_whenSubscriptionDiscountExists() {
        // when
        Order order = new Order(
            mock(User.class),
            null,
            10000L,
            1000L,
            12000L,
            0,
            500L,
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞에 놓아주세요",
            3000L,
            OrderType.CART
        );

        // then
        assertThat(order.getSubscriptionDiscount()).isEqualTo(500L);
    }

    @Test
    @DisplayName("Order 생성 실패 - user가 null이면 예외가 발생한다")
    void createOrder_fail_whenUserIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new Order(
                null,
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
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_039);
    }

    @Test
    @DisplayName("Order 생성 실패 - totalPrice가 null이면 예외가 발생한다")
    void createOrder_fail_whenTotalPriceIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithPrices(
                null,
                0L,
                13000L,
                0,
                null,
                3000L,
                OrderType.CART
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_040);
    }

    @Test
    @DisplayName("Order 생성 실패 - totalPrice가 음수이면 예외가 발생한다")
    void createOrder_fail_whenTotalPriceIsNegative() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithPrices(
                -1L,
                0L,
                13000L,
                0,
                null,
                3000L,
                OrderType.CART
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_040);
    }

    @Test
    @DisplayName("Order 생성 실패 - discountPrice가 null이면 예외가 발생한다")
    void createOrder_fail_whenDiscountPriceIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithPrices(
                10000L,
                null,
                13000L,
                0,
                null,
                3000L,
                OrderType.CART
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_041);
    }

    @Test
    @DisplayName("Order 생성 실패 - discountPrice가 음수이면 예외가 발생한다")
    void createOrder_fail_whenDiscountPriceIsNegative() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithPrices(
                10000L,
                -1L,
                13000L,
                0,
                null,
                3000L,
                OrderType.CART
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_041);
    }

    @Test
    @DisplayName("Order 생성 실패 - finalPrice가 null이면 예외가 발생한다")
    void createOrder_fail_whenFinalPriceIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithPrices(
                10000L,
                0L,
                null,
                0,
                null,
                3000L,
                OrderType.CART
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_042);
    }

    @Test
    @DisplayName("Order 생성 실패 - finalPrice가 음수이면 예외가 발생한다")
    void createOrder_fail_whenFinalPriceIsNegative() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithPrices(
                10000L,
                0L,
                -1L,
                0,
                null,
                3000L,
                OrderType.CART
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_042);
    }

    @Test
    @DisplayName("Order 생성 실패 - usedPoint가 null이면 예외가 발생한다")
    void createOrder_fail_whenUsedPointIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithPrices(
                10000L,
                0L,
                13000L,
                null,
                null,
                3000L,
                OrderType.CART
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_043);
    }

    @Test
    @DisplayName("Order 생성 실패 - usedPoint가 음수이면 예외가 발생한다")
    void createOrder_fail_whenUsedPointIsNegative() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithPrices(
                10000L,
                0L,
                13000L,
                -1,
                null,
                3000L,
                OrderType.CART
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_043);
    }

    @Test
    @DisplayName("Order 생성 실패 - subscriptionDiscount가 음수이면 예외가 발생한다")
    void createOrder_fail_whenSubscriptionDiscountIsNegative() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithPrices(
                10000L,
                0L,
                13000L,
                0,
                -1L,
                3000L,
                OrderType.CART
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_044);
    }

    @Test
    @DisplayName("Order 생성 실패 - receiverName이 null이면 예외가 발생한다")
    void createOrder_fail_whenReceiverNameIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithReceiver(
                null,
                "010-1234-5678",
                "서울시 강남구"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_045);
    }

    @Test
    @DisplayName("Order 생성 실패 - receiverName이 blank이면 예외가 발생한다")
    void createOrder_fail_whenReceiverNameIsBlank() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithReceiver(
                " ",
                "010-1234-5678",
                "서울시 강남구"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_045);
    }

    @Test
    @DisplayName("Order 생성 실패 - receiverPhone이 null이면 예외가 발생한다")
    void createOrder_fail_whenReceiverPhoneIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithReceiver(
                "홍길동",
                null,
                "서울시 강남구"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_046);
    }

    @Test
    @DisplayName("Order 생성 실패 - receiverPhone이 blank이면 예외가 발생한다")
    void createOrder_fail_whenReceiverPhoneIsBlank() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithReceiver(
                "홍길동",
                " ",
                "서울시 강남구"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_046);
    }

    @Test
    @DisplayName("Order 생성 실패 - receiverAddress가 null이면 예외가 발생한다")
    void createOrder_fail_whenReceiverAddressIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithReceiver(
                "홍길동",
                "010-1234-5678",
                null
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_047);
    }

    @Test
    @DisplayName("Order 생성 실패 - receiverAddress가 blank이면 예외가 발생한다")
    void createOrder_fail_whenReceiverAddressIsBlank() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithReceiver(
                "홍길동",
                "010-1234-5678",
                " "
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_047);
    }

    @Test
    @DisplayName("Order 생성 실패 - deliveryFee가 null이면 예외가 발생한다")
    void createOrder_fail_whenDeliveryFeeIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithPrices(
                10000L,
                0L,
                13000L,
                0,
                null,
                null,
                OrderType.CART
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_048);
    }

    @Test
    @DisplayName("Order 생성 실패 - deliveryFee가 음수이면 예외가 발생한다")
    void createOrder_fail_whenDeliveryFeeIsNegative() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithPrices(
                10000L,
                0L,
                13000L,
                0,
                null,
                -1L,
                OrderType.CART
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_048);
    }

    @Test
    @DisplayName("Order 생성 실패 - orderType이 null이면 예외가 발생한다")
    void createOrder_fail_whenOrderTypeIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createOrderWithPrices(
                10000L,
                0L,
                13000L,
                0,
                null,
                3000L,
                null
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_049);
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

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            order::completePayment
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_050);
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

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            order::cancel
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_051);
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

    private Order createOrderWithPrices(
        Long totalPrice,
        Long discountPrice,
        Long finalPrice,
        Integer usedPoint,
        Long subscriptionDiscount,
        Long deliveryFee,
        OrderType orderType
    ) {
        return new Order(
            mock(User.class),
            null,
            totalPrice,
            discountPrice,
            finalPrice,
            usedPoint,
            subscriptionDiscount,
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞에 놔주세요",
            deliveryFee,
            orderType
        );
    }

    private Order createOrderWithReceiver(
        String receiverName,
        String receiverPhone,
        String receiverAddress
    ) {
        return new Order(
            mock(User.class),
            null,
            10000L,
            0L,
            13000L,
            0,
            null,
            receiverName,
            receiverPhone,
            receiverAddress,
            "문 앞에 놔주세요",
            3000L,
            OrderType.CART
        );
    }

}
