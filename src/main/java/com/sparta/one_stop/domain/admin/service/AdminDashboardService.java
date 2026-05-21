package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.dto.DashboardResponse;
import com.sparta.one_stop.domain.delivery.repository.DeliveryRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;

    // 관리자 대시보드 조회
    public DashboardResponse getDashboard() {
        // 주문 현황
        Map<OrderStatus, Long> orderStatusCount = Stream.of(OrderStatus.values())
            .collect(Collectors.toMap(
                status -> status,
                status -> orderRepository.countByStatus(status)
            ));

        // 오늘 주문 수 / 매출
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        long todayOrderCount = orderRepository.countByCreatedAtBetween(startOfDay, endOfDay);

        // 오늘 총 매출 (null 안전 처리)
        long todayRevenue = Optional.ofNullable(
            orderRepository.sumFinalPriceByCreatedAtBetween(startOfDay, endOfDay, OrderStatus.PAID)
        ).orElse(0L);

        // 배송 현황
        Map<DeliveryStatus, Long> deliveryStatusCount = Stream.of(DeliveryStatus.values())
            .collect(Collectors.toMap(
                status -> status,
                status -> deliveryRepository.countByStatus(status)
            ));

        return new DashboardResponse(
            new DashboardResponse.OrderSummary(orderStatusCount, todayOrderCount, todayRevenue),
            new DashboardResponse.DeliverySummary(deliveryStatusCount)
        );

    }
}
