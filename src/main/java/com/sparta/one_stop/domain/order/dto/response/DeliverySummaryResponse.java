package com.sparta.one_stop.domain.order.dto.response;

import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;

public record DeliverySummaryResponse(

    Long deliveryId,

    DeliveryStatus status,

    String deliveryCompany,

    String invoiceNumber
) {

    public static DeliverySummaryResponse of(Delivery delivery) {
        return new DeliverySummaryResponse(
            delivery.getId(),
            delivery.getStatus(),
            delivery.getDeliveryCompany(),
            delivery.getInvoiceNumber()
        );
    }

}
