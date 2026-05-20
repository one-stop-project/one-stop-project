package com.sparta.one_stop.domain.delivery.dto.response;

import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.delivery.entity.DeliveryHistory;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;

import java.time.LocalDateTime;
import java.util.List;

public record DeliveryHistoryResponse(
    Long deliveryId,
    DeliveryStatus currentStatus,
    String invoiceNumber,
    String deliveryCompany,
    List<HistoryItem> history
) {
    public static DeliveryHistoryResponse from(Delivery delivery, List<DeliveryHistory> histories) {
        return new DeliveryHistoryResponse(
            delivery.getId(),
            delivery.getStatus(),
            delivery.getInvoiceNumber(),
            delivery.getDeliveryCompany(),
            histories.stream().map(HistoryItem::from).toList()
        );
    }

    public record HistoryItem(
        DeliveryStatus status,
        String statusDesc,
        LocalDateTime changedAt
    ) {
        public static HistoryItem from(DeliveryHistory history) {
            return new HistoryItem(
                history.getStatus(),
                history.getStatus().getDescription(),
                history.getChangedAt()
            );
        }
    }
}
