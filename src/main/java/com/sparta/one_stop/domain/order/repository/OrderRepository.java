package com.sparta.one_stop.domain.order.repository;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // 내 주문 목록 조회
    // 상태/기간 조건은 선택적으로 적용하고 최신순으로 페이징 조회
    @Query("""
        select o
        from Order o
        where o.user.id = :userId
          and (:status is null or o.status = :status)
          and (:from is null or o.createdAt >= :from)
          and (:to is null or o.createdAt <= :to)
        order by o.createdAt desc
    """)
    Page<Order> searchMyOrders(
        Long userId,
        OrderStatus status,
        LocalDateTime from,
        LocalDateTime to,
        Pageable pageable
    );

    // 상태별 주문 수 조회
    long countByStatus(OrderStatus status);

    // 오늘 주문 수 조회
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    // 오늘 총 매출 조회 (취소 제외, null 안전)
    // 변경 후
    @Query("select coalesce(sum(o.finalPrice), 0) from Order o " +
        "where o.createdAt between :start and :end " +
        "and o.status = :status")
    Long sumFinalPriceByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("status") OrderStatus status);

    // 관리자 전체 주문 조회
    // 상태 / 기간 / 키워드(구매자 이름 or 이메일) 조건 선택적 적용
    @Query("""
    select o
    from Order o
    join fetch o.user u
    where (:status is null or o.status = :status)
      and (:from is null or o.createdAt >= :from)
      and (:to is null or o.createdAt <= :to)
      and (:keyword is null
           or u.name like %:keyword%
           or u.email like %:keyword%)
    order by o.createdAt desc
""")
    Page<Order> searchAllOrders(
        @Param("status") OrderStatus status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("keyword") String keyword,
        Pageable pageable
    );

    // 주문별 상품 수 조회 (N+1 방지용)
    @Query("""
    select o.id, count(oi)
    from Order o
    join OrderItem oi on oi.order.id = o.id
    where o.id in :orderIds
    group by o.id
""")
    List<Object[]> countItemsByOrderIds(@Param("orderIds") List<Long> orderIds);
}
