package com.sparta.one_stop.domain.point.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PointChargeRequest(

    @NotNull(message = "충전 금액은 필수입니다.")
    @Min(value = 1000, message = "포인트는 최소 1,000원 이상 충전할 수 있습니다.")
    @Max(value = 1000000, message = "포인트는 최대 1,000,000원까지 충전할 수 있습니다.")
    Integer amount

) {
}
