package com.sparta.one_stop.domain.payment.entity;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.payment.PaymentMethod;
import com.sparta.one_stop.global.enums.payment.PaymentStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payments")
public class Payment extends BaseEntity {

    // 결제 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    // 주문 정보 (주문 1 : 결제 1)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    // Mock 결제 키
    @Column(name = "payment_key", nullable = false, length = 100)
    private String paymentKey;

    // 결제 금액
    @Column(name = "amount", nullable = false)
    private Long amount;

    // 결제 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    // 결제 수단
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    // 결제 승인 시간
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // 결제 취소 시간
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // == 생성자 ==
    public Payment(
        Order order,
        String paymentKey,
        Long amount,
        PaymentMethod method
    ) {
        validateConstructorArguments(order, paymentKey, amount, method);

        this.order = order;
        this.paymentKey = paymentKey;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.READY;
    }

    // == 검증 메서드 ==

    private void validateConstructorArguments(
        Order order,
        String paymentKey,
        Long amount,
        PaymentMethod method
    ) {
        if (order == null) {
            throw new CustomException(ErrorCode.PAYMENT_020);
        }

        if (paymentKey == null || paymentKey.isBlank()) {
            throw new CustomException(ErrorCode.PAYMENT_021);
        }

        if (amount == null || amount < 0) {
            throw new CustomException(ErrorCode.PAYMENT_022);
        }

        if (method == null) {
            throw new CustomException(ErrorCode.PAYMENT_023);
        }
    }

    // == 비즈니스 메서드 ==

    // 결제 승인
    public void approve() {
        if (this.status != PaymentStatus.READY) {
            throw new CustomException(ErrorCode.PAYMENT_024);
        }

        this.status = PaymentStatus.PAID;
        this.approvedAt = LocalDateTime.now();
    }

    // 결제 실패
    public void fail() {
        if (this.status != PaymentStatus.READY) {
            throw new CustomException(ErrorCode.PAYMENT_025);
        }

        this.status = PaymentStatus.FAILED;
    }

    // 결제 취소
    public void cancel() {
        if (this.status == PaymentStatus.CANCELLED) {
            throw new CustomException(ErrorCode.PAYMENT_026);
        }

        if (this.status != PaymentStatus.PAID) {
            throw new CustomException(ErrorCode.PAYMENT_027);
        }

        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

}
