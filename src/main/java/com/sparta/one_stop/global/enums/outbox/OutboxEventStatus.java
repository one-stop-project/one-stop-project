package com.sparta.one_stop.global.enums.outbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OutboxEventStatus {

    PENDING("발행 대기"),
    PROCESSING("Publisher가 선점하여 처리 중"),
    PUBLISHED("Kafka 발행 성공"),
    DEAD("재시도 초과 또는 수동 확인 필요");

    private final String description;

}
