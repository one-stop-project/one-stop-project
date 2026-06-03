package com.sparta.one_stop.domain.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CancelSubscriptionRequest(
    @NotBlank(message = "해지 사유는 필수입니다.")
    String reason
) {
}
