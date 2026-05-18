package com.sparta.one_stop.domain.product.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_category_mapping",
        indexes = {
                @Index(name = "idx_pcm_product", columnList = "product_id"),
                @Index(name = "idx_pcm_category", columnList = "category_id, product_id"),
                @Index(name = "uk_pcm", columnList = "product_id, category_id", unique = true)
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCategoryMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Builder
    private ProductCategoryMapping(Product product, Category category) {
        this.product = product;
        this.category = category;
    }
}
