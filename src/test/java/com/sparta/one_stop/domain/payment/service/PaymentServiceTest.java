package com.sparta.one_stop.domain.payment.service;

import com.sparta.one_stop.domain.coupon.service.CouponCommandService;
import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.delivery.entity.DeliveryHistory;
import com.sparta.one_stop.domain.delivery.repository.DeliveryHistoryRepository;
import com.sparta.one_stop.domain.delivery.repository.DeliveryRepository;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.payment.dto.request.ApprovePaymentRequest;
import com.sparta.one_stop.domain.payment.dto.response.ApprovePaymentResponse;
import com.sparta.one_stop.domain.payment.entity.Payment;
import com.sparta.one_stop.domain.payment.repository.PaymentRepository;
import com.sparta.one_stop.domain.point.service.PointService;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.payment.PaymentMethod;
import com.sparta.one_stop.global.enums.payment.PaymentStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.outbox.service.OutboxEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DeliveryHistoryRepository deliveryHistoryRepository;

    @Mock
    private PointService pointService;

    @Mock
    private CouponCommandService couponCommandService;

    @Mock
    private OutboxEventService outboxEventService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("approvePayment 성공 - 결제 승인 후 Order, OrderItem, Delivery, DeliveryHistory를 처리한다")
    void approvePayment_success() {
        // given
        Long userId = 1L;
        Long orderId = 10L;
        Long amount = 30000L;

        ApprovePaymentRequest request = new ApprovePaymentRequest(
            orderId,
            amount
        );

        willDoNothing()
            .given(couponCommandService)
            .useCouponByOrder(any(Order.class));

        AtomicReference<OrderStatus> orderStatus =
            new AtomicReference<>(OrderStatus.PENDING_PAYMENT);

        Order order = payableOrder(
            orderId,
            userId,
            amount,
            orderStatus
        );

        OrderItem orderItem1 = orderItem(101L);
        OrderItem orderItem2 = orderItem(102L);

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(false);
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findAllByOrderId(orderId))
            .thenReturn(List.of(orderItem1, orderItem2));
        when(deliveryRepository.findAllByOrderItemIdIn(List.of(101L, 102L)))
            .thenReturn(List.of());
        when(deliveryRepository.saveAll(any()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ApprovePaymentResponse result = paymentService.approvePayment(
            userId,
            request
        );

        // then
        assertThat(result).isNotNull();

        ArgumentCaptor<Payment> paymentCaptor =
            ArgumentCaptor.forClass(Payment.class);

        verify(paymentRepository).save(paymentCaptor.capture());

        Payment savedPayment = paymentCaptor.getValue();

        assertThat(savedPayment.getOrder()).isSameAs(order);
        assertThat(savedPayment.getAmount()).isEqualTo(amount);
        assertThat(savedPayment.getMethod()).isEqualTo(PaymentMethod.MOCK);
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedPayment.getApprovedAt()).isNotNull();

        verify(order).completePayment();
        assertThat(orderStatus.get()).isEqualTo(OrderStatus.PAID);

        verify(orderItem1).markOrdered();
        verify(orderItem2).markOrdered();

        ArgumentCaptor<List<Delivery>> deliveryCaptor =
            ArgumentCaptor.forClass(List.class);

        verify(deliveryRepository).saveAll(deliveryCaptor.capture());

        List<Delivery> savedDeliveries = deliveryCaptor.getValue();

        assertThat(savedDeliveries).hasSize(2);

        ArgumentCaptor<List<DeliveryHistory>> historyCaptor =
            ArgumentCaptor.forClass(List.class);

        verify(deliveryHistoryRepository).saveAll(historyCaptor.capture());

        List<DeliveryHistory> savedHistories = historyCaptor.getValue();

        assertThat(savedHistories).hasSize(2);
    }

    @Test
    @DisplayName("approvePayment 실패 - 존재하지 않는 주문이면 예외 발생")
    void approvePayment_fail_whenOrderDoesNotExist() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        ApprovePaymentRequest request = new ApprovePaymentRequest(
            orderId,
            30000L
        );

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentService.approvePayment(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class);

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("approvePayment 실패 - 본인 주문이 아니면 예외 발생")
    void approvePayment_fail_whenOrderOwnerMismatch() {
        // given
        Long requestUserId = 1L;
        Long orderOwnerId = 2L;
        Long orderId = 10L;

        ApprovePaymentRequest request = new ApprovePaymentRequest(
            orderId,
            30000L
        );

        Order order = orderForOwnerValidation(orderOwnerId);

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentService.approvePayment(
            requestUserId,
            request
        ))
            .isInstanceOf(CustomException.class);

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("approvePayment 실패 - 이미 결제 완료된 주문이면 예외 발생")
    void approvePayment_fail_whenOrderAlreadyPaid() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        ApprovePaymentRequest request = new ApprovePaymentRequest(
            orderId,
            30000L
        );

        Order order = orderForStatusValidation(
            userId,
            OrderStatus.PAID
        );

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentService.approvePayment(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class);

        verify(paymentRepository, never()).existsByOrderId(orderId);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("approvePayment 실패 - 취소된 주문이면 예외 발생")
    void approvePayment_fail_whenOrderCancelled() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        ApprovePaymentRequest request = new ApprovePaymentRequest(
            orderId,
            30000L
        );

        Order order = orderForStatusValidation(
            userId,
            OrderStatus.CANCELLED
        );

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentService.approvePayment(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class);

        verify(paymentRepository, never()).existsByOrderId(orderId);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("approvePayment 실패 - 동일 주문에 대한 Payment가 이미 존재하면 예외 발생")
    void approvePayment_fail_whenPaymentAlreadyExists() {
        // given
        Long userId = 1L;
        Long orderId = 10L;
        Long amount = 30000L;

        ApprovePaymentRequest request = new ApprovePaymentRequest(
            orderId,
            amount
        );

        Order order = orderForExistingPaymentValidation(
            orderId,
            userId
        );

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> paymentService.approvePayment(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class);

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("approvePayment 실패 - 요청 결제 금액과 주문 금액이 다르면 예외 발생")
    void approvePayment_fail_whenAmountMismatch() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        ApprovePaymentRequest request = new ApprovePaymentRequest(
            orderId,
            10000L
        );

        Order order = orderWithStatus(
            orderId,
            userId,
            30000L,
            OrderStatus.PENDING_PAYMENT
        );

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> paymentService.approvePayment(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class);

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("approvePayment 실패 - 주문 상품이 없으면 예외 발생")
    void approvePayment_fail_whenOrderItemsEmpty() {
        // given
        Long userId = 1L;
        Long orderId = 10L;
        Long amount = 30000L;

        ApprovePaymentRequest request = new ApprovePaymentRequest(
            orderId,
            amount
        );

        willDoNothing()
            .given(couponCommandService)
            .useCouponByOrder(any(Order.class));

        AtomicReference<OrderStatus> orderStatus =
            new AtomicReference<>(OrderStatus.PENDING_PAYMENT);

        Order order = payableOrder(
            orderId,
            userId,
            amount,
            orderStatus
        );

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(false);
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findAllByOrderId(orderId))
            .thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> paymentService.approvePayment(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class);

        verify(paymentRepository).save(any(Payment.class));
        verify(order).completePayment();
        verify(deliveryRepository, never()).saveAll(any());
        verify(deliveryHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("approvePayment 실패 - 이미 배송 데이터가 존재하면 예외 발생")
    void approvePayment_fail_whenDeliveryAlreadyExists() {
        // given
        Long userId = 1L;
        Long orderId = 10L;
        Long amount = 30000L;

        ApprovePaymentRequest request = new ApprovePaymentRequest(
            orderId,
            amount
        );

        willDoNothing()
            .given(couponCommandService)
            .useCouponByOrder(any(Order.class));

        AtomicReference<OrderStatus> orderStatus =
            new AtomicReference<>(OrderStatus.PENDING_PAYMENT);

        Order order = payableOrder(
            orderId,
            userId,
            amount,
            orderStatus
        );

        OrderItem orderItem = orderItem(101L);
        Delivery existingDelivery = org.mockito.Mockito.mock(Delivery.class);

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(false);
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findAllByOrderId(orderId))
            .thenReturn(List.of(orderItem));
        when(deliveryRepository.findAllByOrderItemIdIn(List.of(101L)))
            .thenReturn(List.of(existingDelivery));

        // when & then
        assertThatThrownBy(() -> paymentService.approvePayment(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class);

        verify(paymentRepository).save(any(Payment.class));
        verify(order).completePayment();
        verify(deliveryRepository, never()).saveAll(any());
        verify(deliveryHistoryRepository, never()).saveAll(any());
    }

    private Order payableOrder(
        Long orderId,
        Long userId,
        Long finalPrice,
        AtomicReference<OrderStatus> orderStatus
    ) {
        Order order = orderWithStatus(
            orderId,
            userId,
            finalPrice,
            orderStatus.get()
        );

        when(order.getStatus())
            .thenAnswer(invocation -> orderStatus.get());

        doAnswer(invocation -> {
            orderStatus.set(OrderStatus.PAID);
            return null;
        }).when(order).completePayment();

        return order;
    }

    private Order orderWithStatus(
        Long orderId,
        Long userId,
        Long finalPrice,
        OrderStatus status
    ) {
        Order order = orderWithOwner(
            orderId,
            userId
        );

        when(order.getFinalPrice()).thenReturn(finalPrice);
        when(order.getStatus()).thenReturn(status);

        return order;
    }

    private Order orderWithOwner(
        Long orderId,
        Long userId
    ) {
        Order order = org.mockito.Mockito.mock(Order.class);
        User user = org.mockito.Mockito.mock(User.class);

        when(order.getId()).thenReturn(orderId);
        when(order.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);

        return order;
    }

    private OrderItem orderItem(Long orderItemId) {
        OrderItem orderItem = org.mockito.Mockito.mock(OrderItem.class);

        when(orderItem.getId()).thenReturn(orderItemId);

        return orderItem;
    }

    private Order orderForOwnerValidation(Long userId) {
        Order order = org.mockito.Mockito.mock(Order.class);
        User user = org.mockito.Mockito.mock(User.class);

        when(order.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);

        return order;
    }

    private Order orderForStatusValidation(
        Long userId,
        OrderStatus status
    ) {
        Order order = orderForOwnerValidation(userId);

        when(order.getStatus()).thenReturn(status);

        return order;
    }

    private Order orderForExistingPaymentValidation(
        Long orderId,
        Long userId
    ) {
        Order order = orderForStatusValidation(
            userId,
            OrderStatus.PENDING_PAYMENT
        );

        when(order.getId()).thenReturn(orderId);

        return order;
    }

}
