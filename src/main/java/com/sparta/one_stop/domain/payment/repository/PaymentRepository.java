package com.sparta.one_stop.domain.payment.repository;

import com.sparta.one_stop.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 주문 ID 기준 결제 존재 여부 확인
    boolean existsByOrderId(Long orderId);

    // 주문 취소 시 결제 상태 취소 처리용
    Optional<Payment> findByOrderId(Long orderId);

}
