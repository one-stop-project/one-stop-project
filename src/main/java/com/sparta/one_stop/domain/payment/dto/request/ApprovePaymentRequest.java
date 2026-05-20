package com.sparta.one_stop.domain.payment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ApprovePaymentRequest(

    @NotNull(message = "주문 ID는 필수입니다.")
    Long orderId,

    @NotNull(message = "결제 금액은 필수입니다.")
    @Min(value = 0, message = "결제 금액은 0원 이상이어야 합니다.")
    Long amount
) {
}
