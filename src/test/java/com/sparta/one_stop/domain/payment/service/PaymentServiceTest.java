package com.sparta.one_stop.domain.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.coupon.service.CouponCommandService;
import com.sparta.one_stop.domain.delivery.service.DeliveryService;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.payment.dto.request.ApprovePaymentRequest;
import com.sparta.one_stop.domain.payment.dto.response.ApprovePaymentResponse;
import com.sparta.one_stop.domain.payment.entity.Payment;
import com.sparta.one_stop.domain.payment.event.PaymentApprovedEventPayload;
import com.sparta.one_stop.domain.payment.repository.PaymentRepository;
import com.sparta.one_stop.domain.point.payment.PaymentPointGuard;
import com.sparta.one_stop.domain.point.service.PointService;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.payment.PaymentMethod;
import com.sparta.one_stop.global.enums.payment.PaymentStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.outbox.service.OutboxEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PointService pointService;

    @Mock
    private PaymentPointGuard paymentPointGuard;

    @Mock
    private CouponCommandService couponCommandService;

    @Mock
    private DeliveryService deliveryService;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("approvePayment 성공 - 결제 승인 후 배송 생성과 Outbox 이벤트 저장을 처리한다")
    void approvePayment_success() throws Exception {
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

        when(orderRepository.findByIdWithLock(orderId))
            .thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(false);
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any(PaymentApprovedEventPayload.class)))
            .thenReturn("{\"orderId\":1}");

        // when
        ApprovePaymentResponse result = paymentService.approvePayment(
            userId,
            request
        );

        // then
        assertThat(result).isNotNull();

        verify(orderRepository).findByIdWithLock(orderId);

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

        verify(couponCommandService).useCouponByOrder(order);
        verify(deliveryService).createDeliveriesForPayment(order);

        verify(outboxEventService).savePaymentApprovedEvent(
            anyString(),
            eq(orderId),
            anyString()
        );
    }

    @Test
    @DisplayName("approvePayment 성공 - 포인트를 사용한 결제 승인 시 포인트 검증과 차감 후 결제를 완료한다")
    void approvePayment_success_whenUsedPointGreaterThanZero() throws Exception {
        // given
        Long userId = 1L;
        Long orderId = 10L;
        Long amount = 30000L;
        Integer usedPoint = 5000;

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

        when(order.getUsedPoint())
            .thenReturn(usedPoint);

        when(orderRepository.findByIdWithLock(orderId))
            .thenReturn(Optional.of(order));

        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(false);

        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(objectMapper.writeValueAsString(any(PaymentApprovedEventPayload.class)))
            .thenReturn("{\"orderId\":10}");

        // when
        ApprovePaymentResponse result = paymentService.approvePayment(
            userId,
            request
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.finalPrice()).isEqualTo(amount);
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);

        verify(orderRepository).findByIdWithLock(orderId);

        verify(paymentPointGuard).validateBeforePaymentApproval(
            userId,
            usedPoint,
            orderId
        );

        verify(pointService).usePoint(
            userId,
            order,
            usedPoint
        );

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

        verify(couponCommandService).useCouponByOrder(order);
        verify(deliveryService).createDeliveriesForPayment(order);

        verify(outboxEventService).savePaymentApprovedEvent(
            anyString(),
            eq(orderId),
            anyString()
        );
    }

    @Test
    @DisplayName("approvePayment 성공 - Outbox payload 직렬화 실패해도 결제는 성공한다")
    void approvePayment_success_whenOutboxPayloadSerializationFails() throws Exception {
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

        when(orderRepository.findByIdWithLock(orderId))
            .thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(false);
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(objectMapper.writeValueAsString(any(PaymentApprovedEventPayload.class)))
            .thenThrow(new JsonProcessingException("serialize error") {});

        // when
        ApprovePaymentResponse result = paymentService.approvePayment(
            userId,
            request
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.finalPrice()).isEqualTo(amount);
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);

        verify(paymentRepository).save(any(Payment.class));
        verify(order).completePayment();
        verify(couponCommandService).useCouponByOrder(order);
        verify(deliveryService).createDeliveriesForPayment(order);

        verify(outboxEventService, never()).savePaymentApprovedEvent(
            anyString(),
            any(),
            anyString()
        );
    }

    @Test
    @DisplayName("approvePayment 성공 - OutboxEvent 저장 실패해도 결제는 성공한다")
    void approvePayment_success_whenOutboxEventSaveFails() throws Exception {
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

        when(orderRepository.findByIdWithLock(orderId))
            .thenReturn(Optional.of(order));

        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(false);

        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(objectMapper.writeValueAsString(any(PaymentApprovedEventPayload.class)))
            .thenReturn("{\"orderId\":10}");

        doThrow(new CustomException(ErrorCode.OUTBOX_001))
            .when(outboxEventService)
            .savePaymentApprovedEvent(
                anyString(),
                eq(orderId),
                anyString()
            );

        // when
        ApprovePaymentResponse result = paymentService.approvePayment(
            userId,
            request
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.finalPrice()).isEqualTo(amount);
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);

        verify(paymentRepository).save(any(Payment.class));
        verify(order).completePayment();
        assertThat(orderStatus.get()).isEqualTo(OrderStatus.PAID);

        verify(couponCommandService).useCouponByOrder(order);
        verify(deliveryService).createDeliveriesForPayment(order);

        verify(outboxEventService).savePaymentApprovedEvent(
            anyString(),
            eq(orderId),
            anyString()
        );
    }

    @Test
    @DisplayName("approvePayment 실패 - 포인트 차감 실패 시 결제 승인 흐름을 중단한다")
    void approvePayment_fail_whenPointUseFails() {
        // given
        Long userId = 1L;
        Long orderId = 10L;
        Long amount = 30000L;
        Integer usedPoint = 5000;

        ApprovePaymentRequest request = new ApprovePaymentRequest(
            orderId,
            amount
        );

        Order order = orderWithStatus(
            orderId,
            userId,
            amount,
            OrderStatus.PENDING_PAYMENT
        );

        when(order.getUsedPoint())
            .thenReturn(usedPoint);

        when(orderRepository.findByIdWithLock(orderId))
            .thenReturn(Optional.of(order));

        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(false);

        CustomException pointException = new CustomException(ErrorCode.POINT_002);

        doThrow(pointException)
            .when(pointService)
            .usePoint(
                userId,
                order,
                usedPoint
            );

        // when & then
        assertThatThrownBy(() -> paymentService.approvePayment(
            userId,
            request
        ))
            .isSameAs(pointException);

        verify(paymentPointGuard).validateBeforePaymentApproval(
            userId,
            usedPoint,
            orderId
        );

        verify(pointService).usePoint(
            userId,
            order,
            usedPoint
        );

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(order, never()).completePayment();
        verify(couponCommandService, never()).useCouponByOrder(any(Order.class));
        verify(deliveryService, never()).createDeliveriesForPayment(any(Order.class));
        verify(outboxEventService, never()).savePaymentApprovedEvent(
            anyString(),
            any(),
            anyString()
        );
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

        when(orderRepository.findByIdWithLock(orderId))
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

        when(orderRepository.findByIdWithLock(orderId))
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

        when(orderRepository.findByIdWithLock(orderId))
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

        when(orderRepository.findByIdWithLock(orderId))
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
    @DisplayName("approvePayment 실패 - 동일 주문에 대한 Payment가 이미 존재하면 포인트를 차감하지 않고 예외 발생")
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

        when(orderRepository.findByIdWithLock(orderId))
            .thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> paymentService.approvePayment(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class);

        verify(orderRepository).findByIdWithLock(orderId);
        verify(pointService, never()).usePoint(any(), any(), any());
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

        when(orderRepository.findByIdWithLock(orderId))
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
    @DisplayName("approvePayment 실패 - 배송 생성 중 주문 상품이 없으면 예외 발생")
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

        when(orderRepository.findByIdWithLock(orderId))
            .thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(false);
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(new CustomException(ErrorCode.ORDER_001))
            .when(deliveryService)
            .createDeliveriesForPayment(order);

        // when & then
        assertThatThrownBy(() -> paymentService.approvePayment(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class);

        verify(paymentRepository).save(any(Payment.class));
        verify(order).completePayment();
        verify(deliveryService).createDeliveriesForPayment(order);
        verify(outboxEventService, never()).savePaymentApprovedEvent(
            anyString(),
            any(),
            anyString()
        );
    }

    @Test
    @DisplayName("approvePayment 실패 - 배송 생성 중 이미 배송 데이터가 존재하면 예외 발생")
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

        when(orderRepository.findByIdWithLock(orderId))
            .thenReturn(Optional.of(order));
        when(paymentRepository.existsByOrderId(orderId))
            .thenReturn(false);
        when(paymentRepository.save(any(Payment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(new CustomException(ErrorCode.PAYMENT_003))
            .when(deliveryService)
            .createDeliveriesForPayment(order);

        // when & then
        assertThatThrownBy(() -> paymentService.approvePayment(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class);

        verify(paymentRepository).save(any(Payment.class));
        verify(order).completePayment();
        verify(deliveryService).createDeliveriesForPayment(order);
        verify(outboxEventService, never()).savePaymentApprovedEvent(
            anyString(),
            eq(orderId),
            anyString()
        );
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
