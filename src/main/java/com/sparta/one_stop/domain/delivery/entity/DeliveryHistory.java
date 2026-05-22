package com.sparta.one_stop.domain.delivery.entity;

import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    private Delivery delivery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Builder
    public DeliveryHistory(Delivery delivery, DeliveryStatus status) {
        this.delivery = delivery;
        this.status = status;
        this.changedAt = LocalDateTime.now();
    }
}
