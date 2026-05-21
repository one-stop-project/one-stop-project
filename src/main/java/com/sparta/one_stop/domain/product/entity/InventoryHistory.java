package com.sparta.one_stop.domain.product.entity;

import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.product.InventoryHistoryType;
import com.sparta.one_stop.global.enums.product.InventoryRefType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 재고 변동 이력 (입고 / 보정 / 출고)
@Entity
@Table(name = "inventory_history",
        indexes = {
                @Index(name = "idx_ih_item", columnList = "item_id, created_at"),
                @Index(name = "idx_ih_type", columnList = "type, created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private ProductItem productItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private InventoryHistoryType type;

    // 변동 수량 (양수)
    @Column(name = "quantity", nullable = false)
    private Long quantity;

    @Column(name = "before_stock", nullable = false)
    private Long beforeStock;

    @Column(name = "after_stock", nullable = false)
    private Long afterStock;

    @Column(name = "reason", length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", length = 20)
    private InventoryRefType refType;

    @Column(name = "ref_id")
    private Long refId;

    // 처리자 user_id
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Builder
    private InventoryHistory(ProductItem productItem, InventoryHistoryType type, Long quantity,
                             Long beforeStock, Long afterStock, String reason,
                             InventoryRefType refType, Long refId, Long createdBy) {
        this.productItem = productItem;
        this.type = type;
        this.quantity = quantity;
        this.beforeStock = beforeStock;
        this.afterStock = afterStock;
        this.reason = reason;
        this.refType = refType;
        this.refId = refId;
        this.createdBy = createdBy;
    }
}
