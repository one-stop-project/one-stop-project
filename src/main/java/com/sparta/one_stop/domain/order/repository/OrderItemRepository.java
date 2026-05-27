package com.sparta.one_stop.domain.order.repository;

import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // 주문 ID 기준 주문 상품 목록 조회
    List<OrderItem> findAllByOrderId(Long orderId);

    // 주문 목록 조회용
    // 여러 주문 ID에 해당하는 주문 상품을 한 번에 조회하여 주문별 개별 조회 N+1을 방지
    // orderId 기준 그룹핑에 필요한 Order 연관 정보를 함께 조회
    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.order o
        where o.id in :orderIds
    """)
    List<OrderItem> findAllByOrderIdInWithOrder(
        @Param("orderIds") List<Long> orderIds
    );

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

    // 배송 도메인 - 판매자 주문 목록 조회용
    // 결제 전 상태(PENDING_PAYMENT)를 제외한 주문 상품 페이징 조회
    Page<OrderItem> findBySeller_IdAndStatusNot(
        Long sellerId,
        OrderItemStatus status,
        Pageable pageable
    );

    // 리뷰 도메인 - 리뷰 작성용 주문 상품 조회
    // 주문자(Order → User), 상품(Product)까지 fetch join하여
    // 리뷰 작성 시 발생하는 Lazy Loading 및 N+1 문제 방지
    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.order o
        join fetch o.user
        join fetch oi.productItem pi
        join fetch pi.product
        where oi.id = :orderItemId
    """)
    Optional<OrderItem> findForReviewById(
        @Param("orderItemId") Long orderItemId
    );

    // 리뷰 도메인 - 리뷰 작성 가능 목록 조회 (최적화 버전)
    // 리뷰 화면에서 필요한 데이터(Product, Option, OrderUser)까지 한 번에 조회
    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.order o
        join fetch o.user
        join fetch oi.productItem pi
        join fetch pi.product
        where o.user.id = :userId
    """)
    List<OrderItem> findAllReviewableByUserId(@Param("userId") Long userId);
}
