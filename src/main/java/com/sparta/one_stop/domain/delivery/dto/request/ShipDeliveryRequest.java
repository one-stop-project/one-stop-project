package com.sparta.one_stop.domain.delivery.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ShipDeliveryRequest(
    @NotBlank(message = "택배사명은 필수입니다")
    String deliveryCompany,

    @NotBlank(message = "운송장 번호는 필수입니다")
    String invoiceNumber
) {}
