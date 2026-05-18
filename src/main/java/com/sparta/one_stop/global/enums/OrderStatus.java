package com.sparta.one_stop.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PENDING_PAYMENT("결제 대기"),
    PAID("결제 완료"),
    CANCELLED("주문 취소");

    private final String description;

}
