package com.sparta.one_stop.domain.product.entity;

import com.sparta.one_stop.global.enums.product.ProductItemStatus;
import jakarta.persistence.*;
import lombok.*;
//BaseEntity 추가 필요
@Entity
@Table(name = "product_item",
        indexes = {
                @Index(name = "idx_item_product", columnList = "product_id, status")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductItem {

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
            throw new IllegalArgumentException("재고가 부족합니다.");
        }
        this.stock -= quantity;
    }

    public void stop() {
        this.status = ProductItemStatus.STOP;
    }

    public boolean isOnSale() {
        return this.status.isOnSale();
    }
}
