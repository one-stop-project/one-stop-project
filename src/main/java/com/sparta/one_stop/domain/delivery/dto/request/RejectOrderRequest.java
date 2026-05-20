package com.sparta.one_stop.domain.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RejectOrderRequest(
    @NotBlank(message = "거절 사유는 필수입니다")
    String reason
) {}
