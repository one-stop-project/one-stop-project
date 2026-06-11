package com.sparta.one_stop.domain.product.entity;

import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.product.ProductItemStatus;
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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "product_item",
        indexes = {
                @Index(name = "idx_item_product", columnList = "product_id, status"),
                @Index(name = "idx_item_status_price", columnList = "status, price")
        },
        uniqueConstraints = {
                // 동일 상품 내 옵션 조합 중복 방지 (앱 레벨 검증 + DB 최후 방어선)
                @UniqueConstraint(name = "uk_product_item_options",
                        columnNames = {"product_id", "option_value_1", "option_value_2",
                                "option_value_3", "option_value_4", "option_value_5"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "option_value_1", nullable = false, length = 100)
    private String optionValue1;

    @Column(name = "option_value_2", nullable = false, length = 100)
    private String optionValue2;

    @Column(name = "option_value_3", nullable = false, length = 100)
    private String optionValue3;

    @Column(name = "option_value_4", nullable = false, length = 100)
    private String optionValue4;

    @Column(name = "option_value_5", nullable = false, length = 100)
    private String optionValue5;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "stock", nullable = false)
    private Long stock;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductItemStatus status;

    @Builder
    private ProductItem(Product product, String optionValue1, String optionValue2, String optionValue3,
                        String optionValue4, String optionValue5, Long price, Long stock) {
        this.product = product;
        this.optionValue1 = optionValue1;
        this.optionValue2 = optionValue2;
        this.optionValue3 = optionValue3;
        this.optionValue4 = optionValue4;
        this.optionValue5 = optionValue5;
        this.price = price;
        this.stock = stock != null ? stock : 0L;
        this.status = ProductItemStatus.ON_SALE;
    }

    public void update(Long price, String optionValue1, String optionValue2, String optionValue3,
                       String optionValue4, String optionValue5) {
        if (price != null) this.price = price;
        if (optionValue1 != null) this.optionValue1 = optionValue1;
        if (optionValue2 != null) this.optionValue2 = optionValue2;
        if (optionValue3 != null) this.optionValue3 = optionValue3;
        if (optionValue4 != null) this.optionValue4 = optionValue4;
        if (optionValue5 != null) this.optionValue5 = optionValue5;
    }

    public void increaseStock(long quantity) {
        this.stock += quantity;
    }

    public void decreaseStock(long quantity) {
        if (this.stock < quantity) {
            throw new CustomException(ErrorCode.INVENTORY_001);
        }
        this.stock -= quantity;
    }

    // 옵션 보정: 가격/상태/재고(절대값) 중 null이 아닌 필드만 업데이트
    public void updateForAdjustment(Long price, ProductItemStatus status, Long newStock) {
        if (price != null) this.price = price;
        if (status != null) this.status = status;
        if (newStock != null) this.stock = newStock;
    }

    public void stop() {
        this.status = ProductItemStatus.STOP;
    }

    public boolean isOnSale() {
        return this.status.isOnSale();
    }

    // 재고 0 = 품절(구매자 화면 "일시 품절" 표시용). status는 ON_SALE 유지
    public boolean isSoldOut() {
        return this.stock == null || this.stock <= 0;
    }

    // 옵션 정보 요약
    public String getOptionSummary() {

        return Stream.of(
                optionValue1,
                optionValue2,
                optionValue3,
                optionValue4,
                optionValue5
            )
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.joining(" / "));
    }

}
