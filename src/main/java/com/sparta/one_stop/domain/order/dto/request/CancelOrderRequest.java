package com.sparta.one_stop.domain.order.dto.request;

import jakarta.validation.constraints.Size;

public record CancelOrderRequest(

    @Size(max = 255, message = "취소 사유는 255자 이하로 입력해주세요.")
    String reason
) {
}
