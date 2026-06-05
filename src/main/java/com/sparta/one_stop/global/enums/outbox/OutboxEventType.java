package com.sparta.one_stop.global.enums.outbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OutboxEventType {

    PAYMENT_APPROVED("결제 승인 완료 이벤트");

    private final String description;

}
