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
import com.sparta.one_stop.domain.point.service.PointService;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.payment.PaymentMethod;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.outbox.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PointService pointService;
    private final CouponCommandService couponCommandService;
    private final DeliveryService deliveryService;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    /**
     * 결제 승인
     * - Mock 결제 승인 처리
     * - 동일 orderId 중복 결제 승인 방지를 위해 Order를 PESSIMISTIC_WRITE 락으로 조회
     * - 결제 금액과 주문 금액 일치 여부 검증
     * - 결제 승인 전 사용 포인트 실제 차감
     * - 결제 승인 성공 시 적용 쿠폰 사용 처리
     * - Order / Payment 상태를 동일 트랜잭션 내에서 PAID 처리
     * - 결제 승인 완료 시 DeliveryService를 통해 주문 상품 접수 및 배송 생성을 요청
     * - 배송 생성과 배송 이력 기록은 DeliveryService에서 처리
     * - 결제 승인 완료 이벤트를 Outbox 테이블에 저장
     */
    public ApprovePaymentResponse approvePayment(
        Long userId,
        ApprovePaymentRequest request
    ) {

        Order order = findOrder(request.orderId());

        validateOrderOwner(
            userId,
            order
        );

        validatePayableOrder(
            order,
            request.amount()
        );

        // 결제 승인 전 포인트 실제 차감
        pointService.usePoint(
            userId,
            order,
            order.getUsedPoint()
        );

        Payment payment = createApprovedPayment(
            order,
            request.amount()
        );

        order.completePayment();

        couponCommandService.useCouponByOrder(order);

        deliveryService.createDeliveriesForPayment(order);

        // Outbox 이벤트 저장
        savePaymentApprovedOutboxEvent(userId, order, payment);

        return new ApprovePaymentResponse(
            order.getId(),
            order.getFinalPrice(),
            order.getStatus(),
            payment.getApprovedAt()
        );
    }

    /**
     * 결제 승인용 주문 조회
     * - 동일 orderId에 대한 동시 결제 승인 요청을 직렬화하기 위해 PESSIMISTIC_WRITE 락을 획득한다.
     * - 포인트 차감, 쿠폰 사용, Payment 생성 전에 Order 단위 락을 선점하여 중복 결제 승인으로 인한 포인트 이중 차감을 방지한다.
     */
    private Order findOrder(Long orderId) {
        return orderRepository.findByIdWithLock(orderId)
            .orElseThrow(() -> new CustomException(ErrorCode.ORDER_006));
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

    /**
     * 결제 가능 여부 검증
     * - 이미 결제 완료된 주문 재결제 방지
     * - 취소된 주문 결제 방지
     * - 동일 주문에 대한 중복 결제 데이터 생성 방지
     * - 주문 금액과 요청 결제 금액 일치 여부 검증
     */
    private void validatePayableOrder(
        Order order,
        Long amount
    ) {
        if (order.getStatus() == OrderStatus.PAID) {
            throw new CustomException(ErrorCode.PAYMENT_001);
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new CustomException(ErrorCode.PAYMENT_008);
        }

        if (paymentRepository.existsByOrderId(order.getId())) {
            throw new CustomException(ErrorCode.PAYMENT_003);
        }

        if (!order.getFinalPrice().equals(amount)) {
            throw new CustomException(ErrorCode.PAYMENT_002);
        }
    }

    /**
     * Mock 결제 생성 및 승인 처리
     */
    private Payment createApprovedPayment(
        Order order,
        Long amount
    ) {
        Payment payment = new Payment(
            order,
            UUID.randomUUID().toString(),
            amount,
            PaymentMethod.MOCK
        );

        payment.approve();

        return paymentRepository.save(payment);
    }

    /**
     * 결제 승인 Outbox 이벤트 저장
     * - 결제 승인 완료 후 Kafka 발행 대신 Outbox 테이블에 이벤트를 저장한다
     * - eventId는 payment ID 기반으로 생성하여 동일 결제에 대한 중복 이벤트를 방지한다
     * - payload 직렬화 실패 시 예외를 전파하지 않고 로그만 기록한다
     * - Outbox 저장 실패도 예외를 전파하지 않도록 방어한다
     */
    private void savePaymentApprovedOutboxEvent(
        Long userId,
        Order order,
        Payment payment
    ) {
        String eventId = "payment-approved-" + payment.getId();

        PaymentApprovedEventPayload eventPayload = PaymentApprovedEventPayload.of(
            eventId,
            order.getId(),
            payment.getId(),
            userId,
            order.getFinalPrice(),
            payment.getApprovedAt()
        );

        String payloadJson;

        try {
            payloadJson = objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException e) {
            log.error(
                "Outbox 이벤트 payload 직렬화 실패 - orderId: {}, paymentId: {}, eventId: {}",
                order.getId(), payment.getId(), eventId, e
            );
            return;
        }

        try {
            outboxEventService.savePaymentApprovedEvent(
                eventId,
                order.getId(),
                payloadJson
            );
        } catch (Exception e) {
            log.error(
                "Outbox 이벤트 저장 실패 - orderId: {}, paymentId: {}, eventId: {}",
                order.getId(), payment.getId(), eventId, e
            );
        }
    }

}
