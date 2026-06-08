package com.sparta.one_stop.dummy.source;

import com.sparta.one_stop.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 더미 시드 멱등 — "원본 마켓의 개별 변형 listing" ↔ 우리 ProductItem(옵션) 매핑.
// 재실행 시 listingSourceKey로 기존 변형을 찾아 가격만 갱신, 새 변형은 ProductItem 추가.
@Entity
@Table(name = "dummy_product_source_listing",
        indexes = @Index(name = "idx_dummy_listing_base", columnList = "base_source_key"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dummy_source_listing",
                columnNames = {"source", "listing_source_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DummyProductSourceListing extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    // 개별 변형 식별 키 = 원본 raw 기반(NAVER productId 우선, 없으면 brand+maker+full title 정규화)
    @Column(name = "listing_source_key", nullable = false, length = 255)
    private String listingSourceKey;

    // 소속 그룹 키 (같은 기본 상품의 변형 묶음 조회용)
    @Column(name = "base_source_key", nullable = false, length = 255)
    private String baseSourceKey;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    // 이 변형이 매핑된 ProductItem id
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    // 마지막으로 반영한 원본 가격 (변동 시에만 가격 갱신)
    @Column(name = "last_source_price", nullable = false)
    private Long lastSourcePrice;

    // 변형 조합 시그니처(옵션값 묶음) — 재실행 시 변형 변동 감지용
    @Column(name = "variant_signature", length = 255)
    private String variantSignature;

    @Builder
    private DummyProductSourceListing(String source, String listingSourceKey, String baseSourceKey,
                                      Long productId, Long itemId, Long lastSourcePrice, String variantSignature) {
        this.source = source;
        this.listingSourceKey = listingSourceKey;
        this.baseSourceKey = baseSourceKey;
        this.productId = productId;
        this.itemId = itemId;
        this.lastSourcePrice = lastSourcePrice;
        this.variantSignature = variantSignature;
    }

    // 원본 가격 변동 반영 (정책: 재실행 시 가격만 갱신)
    public void updateLastSourcePrice(Long newPrice) {
        this.lastSourcePrice = newPrice;
    }
}
