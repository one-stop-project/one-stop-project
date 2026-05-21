package com.sparta.one_stop.domain.delivery.entity;

import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private OrderItem orderItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Column(name = "delivery_company", length = 50)
    private String deliveryCompany;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Delivery(OrderItem orderItem) {
        this.orderItem = orderItem;
        this.status = DeliveryStatus.ACCEPT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 발주 확인: ACCEPT → INSTRUCT
    public void confirm() {
        validateTransition(DeliveryStatus.INSTRUCT);
        this.status = DeliveryStatus.INSTRUCT;
        this.updatedAt = LocalDateTime.now();
    }

    // 운송장 등록: INSTRUCT → DEPARTURE
    public void registerInvoice(String deliveryCompany, String invoiceNumber) {
        validateTransition(DeliveryStatus.DEPARTURE);
        this.deliveryCompany = deliveryCompany;
        this.invoiceNumber = invoiceNumber;
        this.status = DeliveryStatus.DEPARTURE;
        this.updatedAt = LocalDateTime.now();
    }

    // 배송 상태 변경: DEPARTURE → DELIVERING → FINAL_DELIVERY
    public void updateStatus(DeliveryStatus newStatus) {
        validateTransition(newStatus);
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    private void validateTransition(DeliveryStatus next) {
        boolean valid = switch (this.status) {
            case ACCEPT -> next == DeliveryStatus.INSTRUCT;
            case INSTRUCT -> next == DeliveryStatus.DEPARTURE;
            case DEPARTURE -> next == DeliveryStatus.DELIVERING;
            case DELIVERING -> next == DeliveryStatus.FINAL_DELIVERY;
            case FINAL_DELIVERY -> false;
        };
        if (!valid) {
            throw new CustomException(ErrorCode.SHIPPING_002);
        }
    }

    // 주문 취소 가능 배송 상태 여부
    public boolean isCancelable() {
        return this.status == DeliveryStatus.ACCEPT
            || this.status == DeliveryStatus.INSTRUCT;
    }
    
}
