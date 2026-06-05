package com.sparta.one_stop.domain.ai.entity;

import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_review_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReviewSummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", unique = true, nullable = false,
                foreignKey = @ForeignKey(name = "fk_review_summary_product"))
    private Product product;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String pros;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String cons;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String keywords;

    @Column(nullable = false)
    private String sentiment;

    // 마지막으로 요약에 포함된 리뷰 ID — 증분 업데이트 경계점 (null = 요약 없음)
    @Column(name = "last_included_review_id")
    private Long lastIncludedReviewId;

    // getSummary() 조회 시 추가 count/avg 쿼리를 생략하기 위한 캐시 — 요약 생성·갱신 시 함께 저장
    @Column(name = "review_count", nullable = false)
    private long reviewCount;

    @Column(name = "average_rating", nullable = false)
    private double averageRating;

    // 낙관적 락 — 동시 증분 업데이트 충돌 감지
    @Version
    private Long version;

    @Builder
    public ProductReviewSummary(Product product, String pros, String cons, String keywords,
                                String sentiment, Long lastIncludedReviewId,
                                long reviewCount, double averageRating) {
        this.product = product;
        this.pros = pros;
        this.cons = cons;
        this.keywords = keywords;
        this.sentiment = sentiment;
        this.lastIncludedReviewId = lastIncludedReviewId;
        this.reviewCount = reviewCount;
        this.averageRating = averageRating;
    }

    public void update(String pros, String cons, String keywords, String sentiment,
                       Long lastIncludedReviewId, long reviewCount, double averageRating) {
        this.pros = pros;
        this.cons = cons;
        this.keywords = keywords;
        this.sentiment = sentiment;
        this.lastIncludedReviewId = lastIncludedReviewId;
        this.reviewCount = reviewCount;
        this.averageRating = averageRating;
    }
}
