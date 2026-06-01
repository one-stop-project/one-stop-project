package com.sparta.one_stop.global.enums.point;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PointHistoryType {

    CHARGE("테스트용 충전"),
    EARN("배송 완료 후 적립"),
    USE("주문 결제 시 사용"),
    REFUND("주문 취소로 복구"),
    EXPIRE("만료");

    private final String description;

}
