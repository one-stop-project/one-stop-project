package com.sparta.one_stop.domain.review.entity;

import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.review.ReviewImageStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "review_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewImageStatus status;

    @Builder
    public ReviewImage(
        Review review,
        String imageUrl,
        Integer displayOrder
    ) {
        this.review = review;
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
        this.status = ReviewImageStatus.ACTIVE;
    }

    public void delete() {
        this.status = ReviewImageStatus.DELETED;
    }
}
