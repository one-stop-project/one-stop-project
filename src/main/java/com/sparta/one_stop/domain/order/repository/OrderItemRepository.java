package com.sparta.one_stop.domain.order.repository;

import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // 주문 ID 기준 주문 상품 목록 조회
    List<OrderItem> findAllByOrderId(Long orderId);


    // 배송 도메인 - 판매자 주문 목록 조회용
    // 판매자 본인의 주문 상품만 페이징 조회
    Page<OrderItem> findBySeller_Id(Long sellerId, Pageable pageable);

    // 배송 도메인 - 판매자 주문 상태 필터 조회용
    // 판매자 주문 목록에서 상태 조건 검색
    Page<OrderItem> findBySeller_IdAndStatus(
        Long sellerId,
        OrderItemStatus status,
        Pageable pageable
    );
}
