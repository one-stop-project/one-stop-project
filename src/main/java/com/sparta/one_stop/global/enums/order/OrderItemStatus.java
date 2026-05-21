package com.sparta.one_stop.global.enums.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderItemStatus {

    ORDERED("주문 완료"),
    CONFIRMED("판매자 확인 완료"),
    SHIPPING("배송 중"),
    DELIVERED("배송 완료"),
    REJECTED ("주문 거절");

    private final String description;

}
