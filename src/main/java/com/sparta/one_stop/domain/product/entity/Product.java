package com.sparta.one_stop.domain.product.entity;

import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// idx_product_name_fulltext: FULLTEXT(name, description) — DB 마이그레이션으로 별도 관리
//BaseEntity 추가 필요
@Entity
@Table(name = "product",
        indexes = {
                @Index(name = "idx_product_seller", columnList = "seller_id, status"),
                @Index(name = "idx_product_status", columnList = "status, created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "option_name_1", length = 100)
    private String optionName1;

    @Column(name = "option_name_2", length = 100)
    private String optionName2;

    @Column(name = "option_name_3", length = 100)
    private String optionName3;

    @Column(name = "option_name_4", length = 100)
    private String optionName4;

    @Column(name = "option_name_5", length = 100)
    private String optionName5;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ProductStatus status;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductItem> productItems = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> productImages = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductCategoryMapping> categoryMappings = new ArrayList<>();

    @Builder
    private Product(Seller seller, String name, String description, String thumbnailUrl,
                    String optionName1, String optionName2, String optionName3,
                    String optionName4, String optionName5) {
        this.seller = seller;
        this.name = name;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.optionName1 = optionName1;
        this.optionName2 = optionName2;
        this.optionName3 = optionName3;
        this.optionName4 = optionName4;
        this.optionName5 = optionName5;
        this.status = ProductStatus.APPROVE_REQUESTED;
        this.viewCount = 0L;
        this.salesCount = 0L;
    }

    public void update(String name, String description, String thumbnailUrl) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (thumbnailUrl != null) this.thumbnailUrl = thumbnailUrl;
    }

    public void approve() {
        this.status = ProductStatus.APPROVED;
    }

    public void reject() {
        this.status = ProductStatus.REJECTED;
    }

    public void discontinue() {
        this.status = ProductStatus.DISCONTINUED;
        this.deletedAt = LocalDateTime.now();
    }

    public void forceInactive() {
        this.status = ProductStatus.FORCE_INACTIVE;
    }

    public void syncViewCount(long count) {
        this.viewCount += count;
    }

    public void increaseSalesCount(long count) {
        this.salesCount += count;
    }

    public boolean isApproved() {
        return this.status.isApproved();
    }

    public boolean isEditable() {
        return this.status.isEditable();
    }

    public void addProductItem(ProductItem item) {
        this.productItems.add(item);
    }

    public void addProductImage(ProductImage image) {
        this.productImages.add(image);
    }

    public void addCategoryMapping(ProductCategoryMapping mapping) {
        this.categoryMappings.add(mapping);
    }
}
