package com.sparta.one_stop.domain.product.entity;

import com.sparta.one_stop.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "related_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RelatedProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "relation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_product_id", nullable = false)
    private Product baseProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_product_id", nullable = false)
    private Product targetProduct;

    @Column(name = "relation_weight", nullable = false, columnDefinition = "double default 0")
    private double relationWeight;

    @Builder
    private RelatedProduct(Product baseProduct, Product targetProduct, double relationWeight) {
        this.baseProduct = baseProduct;
        this.targetProduct = targetProduct;
        this.relationWeight = relationWeight;
    }

    public void updateWeight(double relationWeight) {
        this.relationWeight = relationWeight;
    }
}
