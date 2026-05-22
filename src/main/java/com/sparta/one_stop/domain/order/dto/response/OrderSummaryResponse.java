package com.sparta.one_stop.domain.order.dto.response;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.global.enums.order.OrderStatus;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record OrderSummaryResponse(

    Long orderId,

    Long finalPrice,

    OrderStatus status,

    Integer itemCount,

    String firstItemName,

    String firstItemThumbnail,

    LocalDateTime createdAt
) {

    /**
     * 주문 목록 응답 DTO 변환
     * - 주문 상품 목록은 서비스에서 일괄 조회한 결과를 전달받음
     * - 주문별 주문 상품 개별 조회로 인한 N+1 문제를 방지
     * - 대표 상품 썸네일 생성을 위해 Product 정보가 함께 조회되어 있어야 함
     */
    public static OrderSummaryResponse of(
        Order order,
        List<OrderItem> orderItems
    ) {
        OrderItem firstItem = orderItems.stream()
            .min(Comparator.comparing(OrderItem::getId))
            .orElse(null);

        String firstItemName = firstItem != null
            ? firstItem.getItemName()
            : null;

        String firstItemThumbnail = firstItem != null
            ? firstItem.getProductItem()
                .getProduct()
                .getThumbnailUrl()
            : null;

        int itemCount = orderItems.stream()
            .mapToInt(OrderItem::getQuantity)
            .sum();

        return new OrderSummaryResponse(
            order.getId(),
            order.getFinalPrice(),
            order.getStatus(),
            itemCount,
            firstItemName,
            firstItemThumbnail,
            order.getCreatedAt()
        );
    }

}
