package com.sparta.one_stop.domain.order.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderItemRequest(

    @NotNull(message = "상품 옵션 ID는 필수입니다.")
    Long itemId,

    @NotNull(message = "수량은 필수입니다.")
    @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    Integer quantity
) {
}
