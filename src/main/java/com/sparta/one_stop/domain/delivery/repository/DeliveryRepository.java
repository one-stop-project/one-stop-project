package com.sparta.one_stop.domain.delivery.repository;

import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByOrderItem(OrderItem orderItem);

    // orderId로 배송 목록 조회
    List<Delivery> findAllByOrderItem_Order_Id(Long orderId);

    // 상태별 배송 수 조회
    long countByStatus(DeliveryStatus status);

    // 주문 상품 ID 기준 배송 정보 조회
    Optional<Delivery> findByOrderItemId(Long orderItemId);

    // 여러 주문 상품 ID 기준 배송 정보 목록 조회
    List<Delivery> findAllByOrderItemIdIn(Collection<Long> orderItemIds);

}
