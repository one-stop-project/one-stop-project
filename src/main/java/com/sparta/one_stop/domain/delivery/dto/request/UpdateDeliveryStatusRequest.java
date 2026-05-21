package com.sparta.one_stop.domain.delivery.dto.request;

import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDeliveryStatusRequest(
    @NotNull(message = "배송 상태는 필수입니다")
    DeliveryStatus status
) {}
