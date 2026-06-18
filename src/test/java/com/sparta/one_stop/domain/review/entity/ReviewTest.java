package com.sparta.one_stop.domain.review.entity;

import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.review.ReviewStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReviewTest {

    @Test
    @DisplayName("Review 생성 성공 - 초기 상태는 ACTIVE")
    void create_success() {
        // given
        OrderItem orderItem = mock(OrderItem.class);
        Product product = mock(Product.class);
        User user = mock(User.class);

        // when
        Review review = Review.builder()
            .orderItem(orderItem)
            .product(product)
            .user(user)
            .rating(5)
            .content("정말 좋은 상품입니다")
            .build();

        // then
        assertThat(review.getOrderItem()).isSameAs(orderItem);
        assertThat(review.getProduct()).isSameAs(product);
        assertThat(review.getUser()).isSameAs(user);
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("정말 좋은 상품입니다");
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
    }

    @Test
    @DisplayName("리뷰 삭제 - soft delete 후 DELETED 상태")
    void delete_success() {
        // given
        Review review = Review.builder()
            .orderItem(mock(OrderItem.class))
            .product(mock(Product.class))
            .user(mock(User.class))
            .rating(5)
            .content("정말 좋은 상품입니다")
            .build();

        // when
        review.delete();

        // then
        assertThat(review.getStatus()).isEqualTo(ReviewStatus.DELETED);
    }

    @Test
    @DisplayName("리뷰 수정 성공")
    void update_success() {
        // given
        Review review = Review.builder()
            .orderItem(mock(OrderItem.class))
            .product(mock(Product.class))
            .user(mock(User.class))
            .rating(4)
            .content("초기 내용")
            .build();

        // when
        review.update(5, "수정된 리뷰 내용");

        // then
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("수정된 리뷰 내용");
    }

    @Test
    @DisplayName("리뷰 이미지 추가 성공")
    void add_image_success() {
        // given
        Review review = Review.builder()
            .orderItem(mock(OrderItem.class))
            .product(mock(Product.class))
            .user(mock(User.class))
            .rating(5)
            .content("리뷰 내용입니다")
            .build();

        ReviewImage image = mock(ReviewImage.class);

        // when
        review.addImage(image);

        // then
        assertThat(review.getImages()).hasSize(1);
        assertThat(review.getImages().get(0)).isSameAs(image);
    }
}
