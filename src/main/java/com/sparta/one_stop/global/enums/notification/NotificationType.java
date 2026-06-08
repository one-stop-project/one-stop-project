package com.sparta.one_stop.global.enums.notification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {

    PAYMENT_APPROVED("결제 완료");

    // 향후 확장
    // DELIVERY_DEPARTED("배송 출발"),
    // DELIVERY_COMPLETED("배송 완료"),
    // ORDER_RECEIVED("새 주문 접수"),
    // STOCK_LOW("재고 부족"),
    // COUPON_EXPIRING("쿠폰 만료 임박");

    private final String description;

}
