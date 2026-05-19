package com.sparta.one_stop.domain.order.dto.response;

public record DeliverySummaryResponse(

    Long deliveryId,

    String status,

    String deliveryCompany,

    String invoiceNumber
) {
}
