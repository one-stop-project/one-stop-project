package com.sparta.one_stop.domain.admin.dto;

import com.sparta.one_stop.global.enums.OrderStatus;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;

import java.util.Map;

// 관리자 대시보드 응답 DTO
public record DashboardResponse(
    OrderSummary orders,
    DeliverySummary deliveries
) {
    public record OrderSummary(
        Map<OrderStatus, Long> statusCount,
        long todayOrderCount,
        long todayRevenue
    ) {}

    public record DeliverySummary(
        Map<DeliveryStatus, Long> statusCount
    ) {}
}
