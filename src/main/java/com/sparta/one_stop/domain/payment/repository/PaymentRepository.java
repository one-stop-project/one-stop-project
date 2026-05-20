package com.sparta.one_stop.domain.payment.repository;

import com.sparta.one_stop.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 주문 ID 기준 결제 존재 여부 확인
    boolean existsByOrderId(Long orderId);
}
