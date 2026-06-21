package com.sparta.one_stop.domain.product.entity;

import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.product.ProductStatus;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// idx_product_name_fulltext: FULLTEXT(name, description) — DB 마이그레이션으로 별도 관리
@Entity
@Table(name = "product",
        indexes = {
                @Index(name = "idx_product_seller", columnList = "seller_id, status"),
                @Index(name = "idx_product_status", columnList = "status, created_at"),
                @Index(name = "idx_product_sales", columnList = "status, sales_count")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

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

    // 목록 조회 시 옵션 묶음 로딩 (상품마다 따로 부르면 쿼리 많아짐)
    @BatchSize(size = 100)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductItem> productItems = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductImage> productImages = new HashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProductCategoryMapping> categoryMappings = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "product_tag",
        joinColumns = @JoinColumn(name = "product_id"),
        indexes = @Index(name = "idx_product_tag_product_tag", columnList = "product_id, tag"))
    @Column(name = "tag", nullable = false, length = 30)
    private Set<String> tags = new HashSet<>();

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

    // 기본 정보 수정 (null이면 기존 값 유지)
    public void update(String name, String description, String thumbnailUrl) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (thumbnailUrl != null) this.thumbnailUrl = thumbnailUrl;
    }

    // 대표 이미지(display_order=1) 변경 시 썸네일 URL 동기화
    public void changeThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    // 관리자 승인 처리
    public void approve() {
        this.status = ProductStatus.APPROVED;
    }

    // 관리자 반려 처리
    public void reject() {
        this.status = ProductStatus.REJECTED;
    }

    // 판매자 Soft Delete
    public void discontinue() {
        this.status = ProductStatus.DISCONTINUED;
        this.deletedAt = LocalDateTime.now();
    }

    // 관리자 강제 비활성, 판매자 정지 일괄 처리
    public void forceInactive() {
        this.status = ProductStatus.FORCE_INACTIVE;
    }

    // 조회수 동기화
    public void syncViewCount(long count) {
        this.viewCount += count;
    }

    // 판매수 증가
    public void increaseSalesCount(long count) {
        this.salesCount += count;
    }

    // 승인 완료 상태 여부
    public boolean isApproved() {
        return this.status.isApproved();
    }

    // 수정 가능 상태 여부
    public boolean isEditable() {
        return this.status.isEditable();
    }

    // 구매자 노출 가능 여부 (상품 승인 + 판매자 승인 + 판매중 옵션 존재)
    public boolean isVisibleOnSale() {
        if (!isApproved()) return false;
        if (this.seller.getStatus() != SellerStatus.APPROVED) return false;
        if (this.seller.getUser() != null && !this.seller.getUser().isActive()) return false;
        return this.productItems.stream().anyMatch(ProductItem::isOnSale);
    }

    // 옵션 자식 엔티티 추가
    public void addProductItem(ProductItem item) {
        this.productItems.add(item);
    }

    // 이미지 자식 엔티티 추가
    public void addProductImage(ProductImage image) {
        this.productImages.add(image);
    }

    // 카테고리 매핑 자식 추가
    public void addCategoryMapping(ProductCategoryMapping mapping) {
        this.categoryMappings.add(mapping);
    }

    // 태그 전체 교체 (null이면 비움, 저장 전 trim+소문자 정규화)
    public void replaceTags(Set<String> newTags) {
        this.tags.clear();
        if (newTags != null) {
            newTags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.trim().toLowerCase(java.util.Locale.ROOT))
                .forEach(this.tags::add);
        }
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    // REJECTED 상품 수정 시 APPROVE_REQUESTED로 변경 후 재승인 요청
    public void resubmit() {
        this.status = ProductStatus.APPROVE_REQUESTED;
    }
}
