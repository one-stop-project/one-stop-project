package com.sparta.one_stop.domain.order.entity;

import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("OrderItem 생성 실패 - 상품명이 null이면 예외 발생")
    void createOrderItem_fail_whenItemNameIsNull() {
        // when & then
        assertThatThrownBy(() -> new OrderItem(
            mock(Order.class),
            mock(ProductItem.class),
            mock(Seller.class),
            null,
            1,
            10000L,
            "thumbnail.jpg"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("상품명은 필수입니다.");
    }

    @Test
    @DisplayName("OrderItem 생성 실패 - 상품명이 blank이면 예외 발생")
    void createOrderItem_fail_whenItemNameIsBlank() {
        // when & then
        assertThatThrownBy(() -> new OrderItem(
            mock(Order.class),
            mock(ProductItem.class),
            mock(Seller.class),
            " ",
            1,
            10000L,
            "thumbnail.jpg"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("상품명은 필수입니다.");
    }

    @Test
    @DisplayName("OrderItem 생성 실패 - 수량이 1 미만이면 예외 발생")
    void createOrderItem_fail_whenQuantityIsLessThanOne() {
        // when & then
        assertThatThrownBy(() -> new OrderItem(
            mock(Order.class),
            mock(ProductItem.class),
            mock(Seller.class),
            "테스트 상품",
            0,
            10000L,
            "thumbnail.jpg"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("수량은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("OrderItem 생성 실패 - 가격이 0원 미만이면 예외 발생")
    void createOrderItem_fail_whenPriceIsNegative() {
        // when & then
        assertThatThrownBy(() -> new OrderItem(
            mock(Order.class),
            mock(ProductItem.class),
            mock(Seller.class),
            "테스트 상품",
            1,
            -1L,
            "thumbnail.jpg"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("가격은 0원 이상이어야 합니다.");
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

        // when & then
        assertThatThrownBy(orderItem::markOrdered)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("주문 접수는 PENDING_PAYMENT 상태에서만 처리할 수 있습니다.");
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

        // when & then
        assertThatThrownBy(orderItem::confirm)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("주문 확정은 ORDERED 상태에서만 가능합니다.");
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

        // when & then
        assertThatThrownBy(orderItem::startShipping)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("배송 시작은 CONFIRMED 상태에서만 가능합니다.");
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

        // when & then
        assertThatThrownBy(orderItem::completeDelivery)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("배송 완료는 SHIPPING 상태에서만 가능합니다.");
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

        // when & then
        assertThatThrownBy(orderItem::reject)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("주문 거절은 ORDERED 상태에서만 가능합니다.");
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

        // when & then
        assertThatThrownBy(orderItem::cancel)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("현재 상태에서는 주문 상품을 취소할 수 없습니다.");
    }

    @Test
    @DisplayName("cancel 실패 - 배송 완료 상태에서는 취소할 수 없다")
    void cancel_fail_whenStatusIsDelivered() {
        // given
        OrderItem orderItem = deliveredOrderItem();

        // when & then
        assertThatThrownBy(orderItem::cancel)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("현재 상태에서는 주문 상품을 취소할 수 없습니다.");
    }

    @Test
    @DisplayName("cancel 실패 - 거절 상태에서는 취소할 수 없다")
    void cancel_fail_whenStatusIsRejected() {
        // given
        OrderItem orderItem = orderedOrderItem();
        orderItem.reject();

        // when & then
        assertThatThrownBy(orderItem::cancel)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("현재 상태에서는 주문 상품을 취소할 수 없습니다.");
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
