package com.sparta.one_stop.domain.order.entity;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class OrderItemTest {

    @Test
    @DisplayName("OrderItem 생성 성공 - 초기 상태는 PENDING_PAYMENT")
    void createOrderItem_success() {
        // given
        Order order = mock(Order.class);
        ProductItem productItem = mock(ProductItem.class);
        Seller seller = mock(Seller.class);

        // when
        OrderItem orderItem = new OrderItem(
            order,
            productItem,
            seller,
            "테스트 상품",
            2,
            10000L,
            "thumbnail.jpg"
        );

        // then
        assertThat(orderItem.getOrder()).isSameAs(order);
        assertThat(orderItem.getProductItem()).isSameAs(productItem);
        assertThat(orderItem.getSeller()).isSameAs(seller);
        assertThat(orderItem.getItemName()).isEqualTo("테스트 상품");
        assertThat(orderItem.getQuantity()).isEqualTo(2);
        assertThat(orderItem.getPrice()).isEqualTo(10000L);
        assertThat(orderItem.getThumbnailUrl()).isEqualTo("thumbnail.jpg");
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("OrderItem 생성 실패 - 주문 정보가 null이면 예외 발생")
    void createOrderItem_fail_whenOrderIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new OrderItem(
                null,
                mock(ProductItem.class),
                mock(Seller.class),
                "테스트 상품",
                1,
                10000L,
                "thumbnail.jpg"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_020);
    }

    @Test
    @DisplayName("OrderItem 생성 실패 - 상품 옵션 정보가 null이면 예외 발생")
    void createOrderItem_fail_whenProductItemIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new OrderItem(
                mock(Order.class),
                null,
                mock(Seller.class),
                "테스트 상품",
                1,
                10000L,
                "thumbnail.jpg"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_021);
    }

    @Test
    @DisplayName("OrderItem 생성 실패 - 판매자 정보가 null이면 예외 발생")
    void createOrderItem_fail_whenSellerIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new OrderItem(
                mock(Order.class),
                mock(ProductItem.class),
                null,
                "테스트 상품",
                1,
                10000L,
                "thumbnail.jpg"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_022);
    }

    @Test
    @DisplayName("OrderItem 생성 실패 - 상품명이 null이면 예외 발생")
    void createOrderItem_fail_whenItemNameIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new OrderItem(
                mock(Order.class),
                mock(ProductItem.class),
                mock(Seller.class),
                null,
                1,
                10000L,
                "thumbnail.jpg"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_023);
    }

    @Test
    @DisplayName("OrderItem 생성 실패 - 상품명이 blank이면 예외 발생")
    void createOrderItem_fail_whenItemNameIsBlank() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new OrderItem(
                mock(Order.class),
                mock(ProductItem.class),
                mock(Seller.class),
                " ",
                1,
                10000L,
                "thumbnail.jpg"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_023);
    }

    @Test
    @DisplayName("OrderItem 생성 실패 - 수량이 null이면 예외 발생")
    void createOrderItem_fail_whenQuantityIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new OrderItem(
                mock(Order.class),
                mock(ProductItem.class),
                mock(Seller.class),
                "테스트 상품",
                null,
                10000L,
                "thumbnail.jpg"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_024);
    }

    @Test
    @DisplayName("OrderItem 생성 실패 - 수량이 1 미만이면 예외 발생")
    void createOrderItem_fail_whenQuantityIsLessThanOne() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new OrderItem(
                mock(Order.class),
                mock(ProductItem.class),
                mock(Seller.class),
                "테스트 상품",
                0,
                10000L,
                "thumbnail.jpg"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_024);
    }

    @Test
    @DisplayName("OrderItem 생성 실패 - 가격이 null이면 예외 발생")
    void createOrderItem_fail_whenPriceIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new OrderItem(
                mock(Order.class),
                mock(ProductItem.class),
                mock(Seller.class),
                "테스트 상품",
                1,
                null,
                "thumbnail.jpg"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_025);
    }

    @Test
    @DisplayName("OrderItem 생성 실패 - 가격이 0원 미만이면 예외 발생")
    void createOrderItem_fail_whenPriceIsNegative() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new OrderItem(
                mock(Order.class),
                mock(ProductItem.class),
                mock(Seller.class),
                "테스트 상품",
                1,
                -1L,
                "thumbnail.jpg"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_025);
    }

    @Test
    @DisplayName("markOrdered 성공 - PENDING_PAYMENT 상태를 ORDERED로 변경한다")
    void markOrdered_success() {
        // given
        OrderItem orderItem = orderItem();

        // when
        orderItem.markOrdered();

        // then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.ORDERED);
    }

    @Test
    @DisplayName("markOrdered 실패 - PENDING_PAYMENT 상태가 아니면 주문 접수 처리할 수 없다")
    void markOrdered_fail_whenStatusIsNotPendingPayment() {
        // given
        OrderItem orderItem = orderItem();
        orderItem.markOrdered();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            orderItem::markOrdered
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_026);
    }

    @Test
    @DisplayName("confirm 성공 - ORDERED 상태를 CONFIRMED로 변경한다")
    void confirm_success() {
        // given
        OrderItem orderItem = orderedOrderItem();

        // when
        orderItem.confirm();

        // then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.CONFIRMED);
    }

    @Test
    @DisplayName("confirm 실패 - ORDERED 상태가 아니면 주문 확정할 수 없다")
    void confirm_fail_whenStatusIsNotOrdered() {
        // given
        OrderItem orderItem = orderItem();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            orderItem::confirm
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_027);
    }

    @Test
    @DisplayName("startShipping 성공 - CONFIRMED 상태를 SHIPPING으로 변경한다")
    void startShipping_success() {
        // given
        OrderItem orderItem = confirmedOrderItem();

        // when
        orderItem.startShipping();

        // then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.SHIPPING);
    }

    @Test
    @DisplayName("startShipping 실패 - CONFIRMED 상태가 아니면 배송 시작할 수 없다")
    void startShipping_fail_whenStatusIsNotConfirmed() {
        // given
        OrderItem orderItem = orderedOrderItem();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            orderItem::startShipping
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_028);
    }

    @Test
    @DisplayName("completeDelivery 성공 - SHIPPING 상태를 DELIVERED로 변경한다")
    void completeDelivery_success() {
        // given
        OrderItem orderItem = shippingOrderItem();

        // when
        orderItem.completeDelivery();

        // then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.DELIVERED);
    }

    @Test
    @DisplayName("completeDelivery 실패 - SHIPPING 상태가 아니면 배송 완료할 수 없다")
    void completeDelivery_fail_whenStatusIsNotShipping() {
        // given
        OrderItem orderItem = confirmedOrderItem();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            orderItem::completeDelivery
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_029);
    }

    @Test
    @DisplayName("reject 성공 - ORDERED 상태를 REJECTED로 변경한다")
    void reject_success() {
        // given
        OrderItem orderItem = orderedOrderItem();

        // when
        orderItem.reject();

        // then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.REJECTED);
    }

    @Test
    @DisplayName("reject 실패 - ORDERED 상태가 아니면 주문 거절할 수 없다")
    void reject_fail_whenStatusIsNotOrdered() {
        // given
        OrderItem orderItem = orderItem();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            orderItem::reject
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_030);
    }

    @Test
    @DisplayName("cancel 성공 - PENDING_PAYMENT 상태를 CANCELLED로 변경한다")
    void cancel_success_whenStatusIsPendingPayment() {
        // given
        OrderItem orderItem = orderItem();

        // when
        orderItem.cancel();

        // then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel 성공 - ORDERED 상태를 CANCELLED로 변경한다")
    void cancel_success_whenStatusIsOrdered() {
        // given
        OrderItem orderItem = orderedOrderItem();

        // when
        orderItem.cancel();

        // then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel 성공 - CONFIRMED 상태를 CANCELLED로 변경한다")
    void cancel_success_whenStatusIsConfirmed() {
        // given
        OrderItem orderItem = confirmedOrderItem();

        // when
        orderItem.cancel();

        // then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancel 실패 - 배송 시작 이후 상태에서는 취소할 수 없다")
    void cancel_fail_whenStatusIsShipping() {
        // given
        OrderItem orderItem = shippingOrderItem();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            orderItem::cancel
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_031);
    }

    @Test
    @DisplayName("cancel 실패 - 배송 완료 상태에서는 취소할 수 없다")
    void cancel_fail_whenStatusIsDelivered() {
        // given
        OrderItem orderItem = deliveredOrderItem();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            orderItem::cancel
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_031);
    }

    @Test
    @DisplayName("cancel 실패 - 거절 상태에서는 취소할 수 없다")
    void cancel_fail_whenStatusIsRejected() {
        // given
        OrderItem orderItem = orderedOrderItem();
        orderItem.reject();

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            orderItem::cancel
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORDER_031);
    }

    private OrderItem orderItem() {
        return new OrderItem(
            mock(Order.class),
            mock(ProductItem.class),
            mock(Seller.class),
            "테스트 상품",
            1,
            10000L,
            "thumbnail.jpg"
        );
    }

    private OrderItem orderedOrderItem() {
        OrderItem orderItem = orderItem();
        orderItem.markOrdered();

        return orderItem;
    }

    private OrderItem confirmedOrderItem() {
        OrderItem orderItem = orderedOrderItem();
        orderItem.confirm();

        return orderItem;
    }

    private OrderItem shippingOrderItem() {
        OrderItem orderItem = confirmedOrderItem();
        orderItem.startShipping();

        return orderItem;
    }

    private OrderItem deliveredOrderItem() {
        OrderItem orderItem = shippingOrderItem();
        orderItem.completeDelivery();

        return orderItem;
    }

}
