package com.sparta.one_stop.domain.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequest(

    @NotNull(message = "수량은 필수입니다.")
    @Min(value = 0, message = "수량은 0 이상이어야 합니다.")
    @Max(value = 99, message = "수량은 99 이하이어야 합니다.")
    Integer quantity
) {
}
