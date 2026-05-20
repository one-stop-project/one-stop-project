package com.sparta.one_stop.domain.product.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 재고 입고 요청
public record InboundRequest(

        @NotNull(message = "입고 수량은 필수입니다")
        @Min(value = 1, message = "입고 수량은 1 이상이어야 합니다")
        Long quantity,

        @Size(max = 255, message = "사유는 255자 이하여야 합니다")
        String reason
) {
}
