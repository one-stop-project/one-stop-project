package com.sparta.one_stop.domain.review.entity;

import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;


@Entity
@Table(
    name = "review",
    indexes = {
        @Index(
            name = "idx_review_product",
            columnList = "product_id, created_at"
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String content;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewImage> images = new ArrayList<>();

    @Builder
    public Review(
        OrderItem orderItem,
        Product product,
        User user,
        int rating,
        String content
    ) {
        this.orderItem = orderItem;
        this.product = product;
        this.user = user;
        this.rating = rating;
        this.content = content;
    }

    public void update(int rating, String content) {
        this.rating = rating;
        this.content = content;
    }

    public void addImage(ReviewImage image) {
        this.images.add(image);
    }

    public void validateRating() {
        if (rating < 1 || rating > 5) {
            throw new CustomException(ErrorCode.REVIEW_003);
        }
    }

    public void validateContent() {
        if (content == null || content.length() < 10 || content.length() > 1000) {
            throw new CustomException(ErrorCode.REVIEW_004);
        }
    }
}
