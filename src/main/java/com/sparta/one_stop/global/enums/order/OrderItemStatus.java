package com.sparta.one_stop.global.enums.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderItemStatus {

    PENDING_PAYMENT("결제 대기"),
    ORDERED("주문 접수"),
    CONFIRMED("판매자 확인 완료"),
    SHIPPING("배송 중"),
    DELIVERED("배송 완료"),
    CANCELLED("주문 취소"),
    REJECTED("주문 거절");

    private final String description;

}
