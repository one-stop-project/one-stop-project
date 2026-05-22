package com.sparta.one_stop.domain.order.dto.response;

import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;

public record CreateOrderItemResponse(

    Long orderItemId,

    Long itemId,

    String itemName,

    Long price,

    Integer quantity,

    // 주문 상품 소계 = 주문 시점 가격 * 수량
    Long subtotal,

    String sellerName,

    OrderItemStatus status
) {

    /**
     * 주문 상품 엔티티를 주문 생성 응답 DTO로 변환
     * - price는 주문 시점에 저장된 스냅샷 가격 사용
     * - subtotal은 주문 시점 가격과 수량을 기준으로 계산
     */
    public static CreateOrderItemResponse of(OrderItem orderItem) {
        Long subtotal = orderItem.getPrice() * orderItem.getQuantity();

        return new CreateOrderItemResponse(
            orderItem.getId(),
            orderItem.getProductItem().getId(),
            orderItem.getItemName(),
            orderItem.getPrice(),
            orderItem.getQuantity(),
            subtotal,
            orderItem.getSeller().getShopName(),
            orderItem.getStatus()
        );
    }

}
