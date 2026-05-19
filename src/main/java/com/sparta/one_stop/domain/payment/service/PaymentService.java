package com.sparta.one_stop.domain.payment.service;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.payment.dto.request.ApprovePaymentRequest;
import com.sparta.one_stop.domain.payment.dto.response.ApprovePaymentResponse;
import com.sparta.one_stop.domain.payment.entity.Payment;
import com.sparta.one_stop.domain.payment.repository.PaymentRepository;
import com.sparta.one_stop.global.enums.OrderStatus;
import com.sparta.one_stop.global.enums.PaymentMethod;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 결제 승인
     * - Mock 결제 승인 처리
     * - 주문 금액과 요청 금액 일치 여부 검증
     * - Order / Payment 상태를 동일 트랜잭션 내에서 PAID 처리
     */
    public ApprovePaymentResponse approvePayment(
        Long userId,
        ApprovePaymentRequest request
    ) {

        // 주문 조회
        Order order = orderRepository.findById(request.orderId())
            .orElseThrow(() -> new CustomException(ErrorCode.ORDER_007));

        // 본인 주문 검증
        validateOrderOwner(
            userId,
            order
        );

        // 이미 결제된 주문인지 검증
        if (order.getStatus() == OrderStatus.PAID) {
            throw new CustomException(ErrorCode.PAYMENT_002);
        }

        // 취소된 주문 결제 방지
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new CustomException(ErrorCode.ORDER_008);
        }

        // 이미 결제 데이터가 존재하는지 검증
        if (paymentRepository.existsByOrderId(order.getId())) {
            throw new CustomException(ErrorCode.PAYMENT_002);
        }

        // 결제 금액 검증
        if (!order.getFinalPrice().equals(request.amount())) {
            throw new CustomException(ErrorCode.PAYMENT_001);
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

        // TODO: Outbox 이벤트 저장
        // TODO: 배송 ACCEPT 생성 이벤트 연동
        // TODO: 알림 이벤트 연동

        return new ApprovePaymentResponse(
            order.getId(),
            order.getFinalPrice(),
            order.getStatus(),
            payment.getApprovedAt()
        );
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
