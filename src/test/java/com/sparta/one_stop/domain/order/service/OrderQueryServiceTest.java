package com.sparta.one_stop.domain.order.service;

import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.delivery.repository.DeliveryRepository;
import com.sparta.one_stop.domain.order.dto.response.OrderDetailResponse;
import com.sparta.one_stop.domain.order.dto.response.OrderPageResponse;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private DeliveryRepository deliveryRepository;

    @InjectMocks
    private OrderQueryService orderQueryService;

    @Test
    @DisplayName("getMyOrders 성공 - 구매자 본인 주문 목록을 조회한다")
    void getMyOrders_success() {
        // given
        Long userId = 1L;

        Order order1 = orderForSummary(
            10L,
            OrderStatus.PAID
        );
        Order order2 = orderForSummary(
            20L,
            OrderStatus.PENDING_PAYMENT
        );

        OrderItem orderItem1 = orderItemForSummary(
            101L,
            order1
        );
        OrderItem orderItem2 = orderItemForSummary(
            102L,
            order2
        );

        when(orderRepository.searchMyOrders(
            any(),
            any(),
            any(),
            any(),
            any(Pageable.class)
        )).thenReturn(new PageImpl<>(
            List.of(order1, order2)
        ));

        when(orderItemRepository.findAllByOrderIdInWithOrder(List.of(10L, 20L)))
            .thenReturn(List.of(orderItem1, orderItem2));

        // when
        OrderPageResponse result = orderQueryService.getMyOrders(
            userId,
            null,
            null,
            null,
            0,
            20
        );

        // then
        assertThat(result).isNotNull();

        verify(orderRepository).searchMyOrders(
            any(),
            any(),
            any(),
            any(),
            any(Pageable.class)
        );
        verify(orderItemRepository).findAllByOrderIdInWithOrder(List.of(10L, 20L));

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).orderId()).isEqualTo(10L);
        assertThat(result.content().get(0).status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("getMyOrders 성공 - 주문 목록이 비어 있으면 OrderItem 조회를 수행하지 않는다")
    void getMyOrders_success_whenOrdersEmpty() {
        // given
        Long userId = 1L;

        when(orderRepository.searchMyOrders(
            any(),
            any(),
            any(),
            any(),
            any(Pageable.class)
        )).thenReturn(new PageImpl<>(
            List.of()
        ));

        // when
        OrderPageResponse result = orderQueryService.getMyOrders(
            userId,
            null,
            null,
            null,
            0,
            20
        );

        // then
        assertThat(result).isNotNull();

        verify(orderItemRepository, never()).findAllByOrderIdInWithOrder(any());
    }

    @Test
    @DisplayName("getMyOrders 성공 - status, from, to 조건을 Repository 조회 조건으로 전달한다")
    void getMyOrders_success_passSearchConditions() {
        // given
        Long userId = 1L;
        OrderStatus status = OrderStatus.PAID;
        LocalDate from = LocalDate.of(
            2026,
            5,
            1
        );
        LocalDate to = LocalDate.of(
            2026,
            5,
            31
        );

        when(orderRepository.searchMyOrders(
            any(),
            any(),
            any(),
            any(),
            any(Pageable.class)
        )).thenReturn(new PageImpl<>(
            List.of()
        ));

        // when
        orderQueryService.getMyOrders(
            userId,
            status,
            from,
            to,
            1,
            10
        );

        // then
        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<OrderStatus> statusCaptor = ArgumentCaptor.forClass(OrderStatus.class);
        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(orderRepository).searchMyOrders(
            userIdCaptor.capture(),
            statusCaptor.capture(),
            fromCaptor.capture(),
            toCaptor.capture(),
            pageableCaptor.capture()
        );

        assertThat(userIdCaptor.getValue()).isEqualTo(userId);
        assertThat(statusCaptor.getValue()).isEqualTo(status);
        assertThat(fromCaptor.getValue()).isEqualTo(from.atStartOfDay());
        assertThat(toCaptor.getValue()).isEqualTo(to.atTime(LocalTime.MAX));
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("getOrder 성공 - 주문 상세를 조회한다")
    void getOrder_success() {
        // given
        Long userId = 1L;
        Long orderId = 10L;
        Long orderItemId = 101L;

        Order order = orderForDetail(
            orderId,
            userId,
            OrderStatus.PAID
        );

        OrderItem orderItem = orderItemForDetail(orderItemId);

        Delivery delivery = delivery(
            orderItem
        );

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(orderId))
            .thenReturn(List.of(orderItem));
        when(deliveryRepository.findAllByOrderItemIdIn(List.of(orderItemId)))
            .thenReturn(List.of(delivery));

        // when
        OrderDetailResponse result = orderQueryService.getOrder(
            userId,
            orderId
        );

        // then
        assertThat(result).isNotNull();

        verify(orderRepository).findById(orderId);
        verify(orderItemRepository).findAllByOrderId(orderId);
        verify(deliveryRepository).findAllByOrderItemIdIn(List.of(orderItemId));

        assertThat(result.orderItems().get(0).orderItemId()).isEqualTo(orderItemId);
        assertThat(result.orderItems().get(0).itemId()).isEqualTo(1001L);
        assertThat(result.orderItems().get(0).sellerId()).isEqualTo(1L);
        assertThat(result.orderItems().get(0).itemName()).isEqualTo("테스트 상품");
        assertThat(result.orderItems().get(0).quantity()).isEqualTo(2);
        assertThat(result.orderItems().get(0).price()).isEqualTo(10000L);
        assertThat(result.orderItems().get(0).status()).isEqualTo(OrderItemStatus.ORDERED);
    }

    @Test
    @DisplayName("getOrder 실패 - 존재하지 않는 주문이면 예외 발생")
    void getOrder_fail_whenOrderDoesNotExist() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderQueryService.getOrder(
            userId,
            orderId
        )).isInstanceOf(CustomException.class);

        verify(orderItemRepository, never()).findAllByOrderId(any());
        verify(deliveryRepository, never()).findAllByOrderItemIdIn(any());
    }

    @Test
    @DisplayName("getOrder 실패 - 본인 주문이 아니면 예외 발생")
    void getOrder_fail_whenOrderOwnerMismatch() {
        // given
        Long requestUserId = 1L;
        Long orderOwnerId = 2L;
        Long orderId = 10L;

        Order order = orderForOwnerValidation(orderOwnerId);

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> orderQueryService.getOrder(
            requestUserId,
            orderId
        )).isInstanceOf(CustomException.class);

        verify(orderItemRepository, never()).findAllByOrderId(orderId);
        verify(deliveryRepository, never()).findAllByOrderItemIdIn(any());
    }

    private Order orderForOwnerValidation(Long userId) {
        Order order = mock(Order.class);
        User user = mock(User.class);

        when(order.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);

        return order;
    }

    private Delivery delivery(OrderItem orderItem) {
        Delivery delivery = mock(Delivery.class);

        when(delivery.getOrderItem()).thenReturn(orderItem);

        return delivery;
    }

    private Order orderForDetail(
        Long orderId,
        Long userId,
        OrderStatus status
    ) {
        Order order = mock(Order.class);
        User user = mock(User.class);

        when(order.getId()).thenReturn(orderId);
        when(order.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);

        lenient().when(order.getStatus()).thenReturn(status);
        lenient().when(order.getDeliveryFee()).thenReturn(3000L);
        lenient().when(order.getTotalPrice()).thenReturn(20000L);
        lenient().when(order.getDiscountPrice()).thenReturn(0L);
        lenient().when(order.getUsedPoint()).thenReturn(0);
        lenient().when(order.getFinalPrice()).thenReturn(23000L);
        lenient().when(order.getReceiverName()).thenReturn("홍길동");
        lenient().when(order.getReceiverPhone()).thenReturn("010-1234-5678");
        lenient().when(order.getReceiverAddress()).thenReturn("서울시 강남구");

        return order;
    }

    private Order orderForSummary(
        Long orderId,
        OrderStatus status
    ) {
        Order order = mock(Order.class);

        when(order.getId()).thenReturn(orderId);

        lenient().when(order.getStatus()).thenReturn(status);
        lenient().when(order.getDeliveryFee()).thenReturn(3000L);
        lenient().when(order.getTotalPrice()).thenReturn(20000L);
        lenient().when(order.getDiscountPrice()).thenReturn(0L);
        lenient().when(order.getUsedPoint()).thenReturn(0);
        lenient().when(order.getFinalPrice()).thenReturn(23000L);

        return order;
    }

    private OrderItem orderItemForSummary(
        Long orderItemId,
        Order order
    ) {
        OrderItem orderItem = mock(OrderItem.class);

        when(orderItem.getOrder()).thenReturn(order);

        lenient().when(orderItem.getId()).thenReturn(orderItemId);
        lenient().when(orderItem.getItemName()).thenReturn("테스트 상품");
        lenient().when(orderItem.getQuantity()).thenReturn(2);
        lenient().when(orderItem.getPrice()).thenReturn(10000L);
        lenient().when(orderItem.getThumbnailUrl()).thenReturn("thumbnail.jpg");
        lenient().when(orderItem.getStatus()).thenReturn(OrderItemStatus.ORDERED);

        return orderItem;
    }

    private OrderItem orderItemForDetail(Long orderItemId) {
        OrderItem orderItem = mock(OrderItem.class);
        ProductItem productItem = mock(ProductItem.class);
        Seller seller = mock(Seller.class);

        when(orderItem.getId()).thenReturn(orderItemId);
        when(orderItem.getProductItem()).thenReturn(productItem);
        when(productItem.getId()).thenReturn(1001L);
        when(orderItem.getSeller()).thenReturn(seller);
        when(seller.getId()).thenReturn(1L);

        lenient().when(orderItem.getItemName()).thenReturn("테스트 상품");
        lenient().when(orderItem.getQuantity()).thenReturn(2);
        lenient().when(orderItem.getPrice()).thenReturn(10000L);
        lenient().when(orderItem.getThumbnailUrl()).thenReturn("thumbnail.jpg");
        lenient().when(orderItem.getStatus()).thenReturn(OrderItemStatus.ORDERED);

        return orderItem;
    }

}
