package com.sparta.one_stop.global.enums.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderCancelType {

    BUYER_CANCEL("구매자 주문 취소"),
    SELLER_REJECT("판매자 주문 거절"),
    ADMIN_CANCEL("관리자 강제 취소"),
    PAYMENT_FAILED("결제 실패로 인한 주문 취소"),
    PAYMENT_CANCELLED("결제 취소/환불로 인한 주문 취소"),
    SYSTEM_CANCEL("시스템 자동 취소");

    private final String description;

}
