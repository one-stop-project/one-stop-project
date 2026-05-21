package com.sparta.one_stop.domain.payment.service;

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
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.payment.PaymentMethod;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryHistoryRepository deliveryHistoryRepository;

    /**
     * 결제 승인
     * - Mock 결제 승인 처리
     * - 주문 금액과 요청 금액 일치 여부 검증
     * - Order / Payment 상태를 동일 트랜잭션 내에서 PAID 처리
     * - 결제 승인 완료 시 주문 상품별 Delivery 생성
     * - 최초 배송 상태는 ACCEPT
     */
    public ApprovePaymentResponse approvePayment(
        Long userId,
        ApprovePaymentRequest request
    ) {

        // 주문 조회
        Order order = orderRepository.findById(request.orderId())
            .orElseThrow(() -> new CustomException(ErrorCode.ORDER_006));

        // 본인 주문 검증
        validateOrderOwner(
            userId,
            order
        );

        // 이미 결제된 주문인지 검증
        if (order.getStatus() == OrderStatus.PAID) {
            throw new CustomException(ErrorCode.PAYMENT_001);
        }

        // 취소된 주문 결제 방지
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new CustomException(ErrorCode.PAYMENT_008);
        }

        // 이미 결제 데이터가 존재하는지 검증
        if (paymentRepository.existsByOrderId(order.getId())) {
            throw new CustomException(ErrorCode.PAYMENT_003);
        }

        // 결제 금액 검증
        if (!order.getFinalPrice().equals(request.amount())) {
            throw new CustomException(ErrorCode.PAYMENT_002);
        }

        // 결제 생성
        Payment payment = new Payment(
            order,
            UUID.randomUUID().toString(),
            request.amount(),
            PaymentMethod.MOCK
        );

        // 결제 승인 처리
        payment.approve();

        paymentRepository.save(payment);

        // 주문 상태 결제 완료 처리
        order.completePayment();

        // 결제 승인 완료 후 주문 상품별 배송 생성
        createDeliveries(order);

        // TODO: Outbox 이벤트 저장
        // TODO: 알림 이벤트 연동

        return new ApprovePaymentResponse(
            order.getId(),
            order.getFinalPrice(),
            order.getStatus(),
            payment.getApprovedAt()
        );
    }

    /**
     * 결제 승인 완료 후 주문 상품별 배송 생성
     * - 주문 상품 1개당 Delivery 1개 생성
     * - 최초 배송 상태는 ACCEPT
     * - 배송 이력에도 ACCEPT 기록
     */
    private void createDeliveries(Order order) {

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(
            order.getId()
        );

        if (orderItems.isEmpty()) {
            throw new CustomException(ErrorCode.ORDER_001);
        }

        List<Long> orderItemIds = orderItems.stream()
            .map(OrderItem::getId)
            .toList();

        // 동일 주문에 대한 배송 중복 생성 방지
        List<Delivery> existingDeliveries = deliveryRepository.findAllByOrderItemIdIn(
            orderItemIds
        );

        if (!existingDeliveries.isEmpty()) {
            throw new CustomException(ErrorCode.PAYMENT_003);
        }

        List<Delivery> deliveries = orderItems.stream()
            .map(orderItem -> {
                orderItem.markOrdered();

                return Delivery.builder()
                    .orderItem(orderItem)
                    .build();
            })
            .toList();

        List<Delivery> savedDeliveries = deliveryRepository.saveAll(deliveries);

        List<DeliveryHistory> histories = savedDeliveries.stream()
            .map(delivery -> new DeliveryHistory(
                delivery,
                DeliveryStatus.ACCEPT
            ))
            .toList();

        deliveryHistoryRepository.saveAll(histories);
    }

    /**
     * 주문 소유자 검증
     */
    private void validateOrderOwner(
        Long userId,
        Order order
    ) {
        if (!order.getUser()
            .getId()
            .equals(userId)) {

            throw new CustomException(ErrorCode.ORDER_007);
        }
    }

}
