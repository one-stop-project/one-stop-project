package com.sparta.one_stop.domain.order.entity;

import com.sparta.one_stop.global.enums.order.CancelActorType;
import com.sparta.one_stop.global.enums.order.OrderCancelType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OrderCancelHistoryTest {

    @Test
    @DisplayName("OrderCancelHistory 생성 성공 - 구매자 취소 이력을 생성한다")
    void createOrderCancelHistory_success_whenActorTypeIsBuyer() {
        // given
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);

        // when
        OrderCancelHistory history = new OrderCancelHistory(
            order,
            orderItem,
            CancelActorType.BUYER,
            1L,
            OrderCancelType.BUYER_CANCEL,
            "단순 변심",
            30000L,
            0
        );

        // then
        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getOrderItem()).isSameAs(orderItem);
        assertThat(history.getActorType()).isEqualTo(CancelActorType.BUYER);
        assertThat(history.getActorId()).isEqualTo(1L);
        assertThat(history.getCancelType()).isEqualTo(OrderCancelType.BUYER_CANCEL);
        assertThat(history.getReason()).isEqualTo("단순 변심");
        assertThat(history.getCancelledPrice()).isEqualTo(30000L);
        assertThat(history.getRestoredPoint()).isEqualTo(0);
    }

    @Test
    @DisplayName("OrderCancelHistory 생성 성공 - 시스템 취소 이력을 생성한다")
    void createOrderCancelHistory_success_whenActorTypeIsSystem() {
        // given
        Order order = mock(Order.class);

        // when
        OrderCancelHistory history = new OrderCancelHistory(
            order,
            null,
            CancelActorType.SYSTEM,
            null,
            OrderCancelType.SYSTEM_CANCEL,
            "시스템 취소",
            30000L,
            0
        );

        // then
        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getOrderItem()).isNull();
        assertThat(history.getActorType()).isEqualTo(CancelActorType.SYSTEM);
        assertThat(history.getActorId()).isNull();
        assertThat(history.getCancelType()).isEqualTo(OrderCancelType.SYSTEM_CANCEL);
        assertThat(history.getReason()).isEqualTo("시스템 취소");
        assertThat(history.getCancelledPrice()).isEqualTo(30000L);
        assertThat(history.getRestoredPoint()).isEqualTo(0);
    }

    @Test
    @DisplayName("OrderCancelHistory 생성 실패 - 주문 정보가 null이면 예외 발생")
    void createOrderCancelHistory_fail_whenOrderIsNull() {
        // when & then
        assertThatThrownBy(() -> new OrderCancelHistory(
            null,
            null,
            CancelActorType.BUYER,
            1L,
            OrderCancelType.BUYER_CANCEL,
            "단순 변심",
            30000L,
            0
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("주문 정보는 필수입니다.");
    }

    @Test
    @DisplayName("OrderCancelHistory 생성 실패 - 처리 주체 유형이 null이면 예외 발생")
    void createOrderCancelHistory_fail_whenActorTypeIsNull() {
        // when & then
        assertThatThrownBy(() -> new OrderCancelHistory(
            mock(Order.class),
            null,
            null,
            1L,
            OrderCancelType.BUYER_CANCEL,
            "단순 변심",
            30000L,
            0
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("처리 주체 유형은 필수입니다.");
    }

    @Test
    @DisplayName("OrderCancelHistory 생성 실패 - 취소/거절 유형이 null이면 예외 발생")
    void createOrderCancelHistory_fail_whenCancelTypeIsNull() {
        // when & then
        assertThatThrownBy(() -> new OrderCancelHistory(
            mock(Order.class),
            null,
            CancelActorType.BUYER,
            1L,
            null,
            "단순 변심",
            30000L,
            0
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("취소/거절 유형은 필수입니다.");
    }

    @Test
    @DisplayName("OrderCancelHistory 생성 실패 - actorId가 필수인 처리 주체인데 actorId가 null이면 예외 발생")
    void createOrderCancelHistory_fail_whenRequiredActorIdIsNull() {
        // when & then
        assertThatThrownBy(() -> new OrderCancelHistory(
            mock(Order.class),
            null,
            CancelActorType.BUYER,
            null,
            OrderCancelType.BUYER_CANCEL,
            "단순 변심",
            30000L,
            0
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("처리자 ID는 필수입니다.");
    }

    @Test
    @DisplayName("OrderCancelHistory 생성 실패 - actorId가 필요 없는 처리 주체인데 actorId가 존재하면 예외 발생")
    void createOrderCancelHistory_fail_whenUnnecessaryActorIdExists() {
        // when & then
        assertThatThrownBy(() -> new OrderCancelHistory(
            mock(Order.class),
            null,
            CancelActorType.SYSTEM,
            1L,
            OrderCancelType.SYSTEM_CANCEL,
            "시스템 취소",
            30000L,
            0
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("해당 처리 주체는 actorId를 가질 수 없습니다.");
    }

    @Test
    @DisplayName("OrderCancelHistory 생성 실패 - actorId가 0 이하이면 예외 발생")
    void createOrderCancelHistory_fail_whenActorIdIsLessThanOne() {
        // when & then
        assertThatThrownBy(() -> new OrderCancelHistory(
            mock(Order.class),
            null,
            CancelActorType.BUYER,
            0L,
            OrderCancelType.BUYER_CANCEL,
            "단순 변심",
            30000L,
            0
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("처리자 ID는 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("OrderCancelHistory 생성 실패 - 취소/거절 금액이 null이면 예외 발생")
    void createOrderCancelHistory_fail_whenCancelledPriceIsNull() {
        // when & then
        assertThatThrownBy(() -> new OrderCancelHistory(
            mock(Order.class),
            null,
            CancelActorType.BUYER,
            1L,
            OrderCancelType.BUYER_CANCEL,
            "단순 변심",
            null,
            0
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("취소/거절 금액은 0원 이상이어야 합니다.");
    }

    @Test
    @DisplayName("OrderCancelHistory 생성 실패 - 취소/거절 금액이 음수이면 예외 발생")
    void createOrderCancelHistory_fail_whenCancelledPriceIsNegative() {
        // when & then
        assertThatThrownBy(() -> new OrderCancelHistory(
            mock(Order.class),
            null,
            CancelActorType.BUYER,
            1L,
            OrderCancelType.BUYER_CANCEL,
            "단순 변심",
            -1L,
            0
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("취소/거절 금액은 0원 이상이어야 합니다.");
    }

    @Test
    @DisplayName("OrderCancelHistory 생성 실패 - 복구 포인트가 null이면 예외 발생")
    void createOrderCancelHistory_fail_whenRestoredPointIsNull() {
        // when & then
        assertThatThrownBy(() -> new OrderCancelHistory(
            mock(Order.class),
            null,
            CancelActorType.BUYER,
            1L,
            OrderCancelType.BUYER_CANCEL,
            "단순 변심",
            30000L,
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("복구 포인트는 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("OrderCancelHistory 생성 실패 - 복구 포인트가 음수이면 예외 발생")
    void createOrderCancelHistory_fail_whenRestoredPointIsNegative() {
        // when & then
        assertThatThrownBy(() -> new OrderCancelHistory(
            mock(Order.class),
            null,
            CancelActorType.BUYER,
            1L,
            OrderCancelType.BUYER_CANCEL,
            "단순 변심",
            30000L,
            -1
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("복구 포인트는 0 이상이어야 합니다.");
    }

}
