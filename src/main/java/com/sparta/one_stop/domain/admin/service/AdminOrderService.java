package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.dto.AdminOrderResponse;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOrderService {

    private final OrderRepository orderRepository;

    public Page<AdminOrderResponse> getOrders(
        OrderStatus status,
        LocalDate from,
        LocalDate to,
        String keyword,
        Pageable pageable
    ) {
        // LocalDate → LocalDateTime 변환
        // from은 해당 날짜 00:00:00, to는 23:59:59로 설정
        LocalDateTime fromDateTime = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDateTime = to != null ? to.atTime(LocalTime.MAX) : null;

        // from이 to보다 이후면 예외 처리
        if (fromDateTime != null && toDateTime != null
            && fromDateTime.isAfter(toDateTime)) {
            throw new CustomException(ErrorCode.COMMON_001, "기간 조건이 올바르지 않습니다.");
        }

        // 빈 문자열은 null로 처리 (검색 조건 미적용)
        String searchKeyword = (keyword != null && !keyword.isBlank()) ? keyword : null;

        Page<Order> orders = orderRepository.searchAllOrders(
            status, fromDateTime, toDateTime, searchKeyword, pageable
        );

        // 주문 ID 목록 추출
        List<Long> orderIds = orders.getContent().stream()
            .map(Order::getId)
            .toList();

        // 주문별 상품 수 일괄 조회 (N+1 방지)
        List<Object[]> countResult;
        if (orderIds.isEmpty()) {
            countResult = List.of();
        } else {
            countResult = orderRepository.countItemsByOrderIds(orderIds);
        }

        Map<Long, Integer> itemCountMap = countResult.stream()
            .collect(Collectors.toMap(
                row -> (Long) row[0],
                row -> ((Long) row[1]).intValue()
            ));

        return orders.map(order ->
            AdminOrderResponse.from(order, itemCountMap.getOrDefault(order.getId(), 0))
        );


    }
}
