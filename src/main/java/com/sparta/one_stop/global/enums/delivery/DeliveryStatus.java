package com.sparta.one_stop.global.enums.delivery;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeliveryStatus {

    ACCEPT("결제완료"),
    INSTRUCT("상품준비중"),
    DEPARTURE("배송지시"),
    DELIVERING("배송중"),
    FINAL_DELIVERY("배송완료"),
    ORDER_CANCELLED("주문취소");

    private final String description;
}
