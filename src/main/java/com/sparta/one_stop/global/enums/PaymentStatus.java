package com.sparta.one_stop.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    READY("결제 대기"),
    PAID("결제 완료"),
    FAILED("결제 실패"),
    CANCELLED("결제 취소");

    private final String description;

}
