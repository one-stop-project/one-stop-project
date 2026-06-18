package com.sparta.one_stop.domain.review.service;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.review.dto.request.CreateReviewRequest;
import com.sparta.one_stop.domain.review.dto.request.UpdateReviewRequest;
import com.sparta.one_stop.domain.review.entity.Review;
import com.sparta.one_stop.domain.review.repository.ReviewRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.review.ReviewStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.security.AuthUser;
import com.sparta.one_stop.global.storage.ImageStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ImageStorage imageStorage;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReviewService reviewService;

    private AuthUser authUser(Long userId) {
        AuthUser au = mock(AuthUser.class);
        lenient().when(au.userId()).thenReturn(userId);
        return au;
    }

    private Review reviewOwnedBy(Long userId) {
        Review rv = mock(Review.class);
        User user = mock(User.class);
        Product product = mock(Product.class);
        OrderItem orderItem = mock(OrderItem.class);

        lenient().when(rv.getUser()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(userId);

        lenient().when(rv.getProduct()).thenReturn(product);
        lenient().when(product.getId()).thenReturn(10L);

        lenient().when(rv.getCreatedAt()).thenReturn(LocalDateTime.now());
        lenient().when(rv.getStatus()).thenReturn(ReviewStatus.ACTIVE);
        lenient().when(rv.getImages()).thenReturn(new ArrayList<>());

        lenient().when(rv.getOrderItem()).thenReturn(orderItem);
        lenient().when(orderItem.getId()).thenReturn(1L);

        return rv;
    }

    private OrderItem deliveredOrderItem(Long orderItemId, Long userId) {
        OrderItem oi = mock(OrderItem.class);
        Order ord = mock(Order.class);
        User user = mock(User.class);
        ProductItem pi = mock(ProductItem.class);
        Product product = mock(Product.class);

        lenient().when(oi.getId()).thenReturn(orderItemId);
        lenient().when(oi.getOrder()).thenReturn(ord);
        lenient().when(ord.getUser()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(userId);
        lenient().when(oi.getStatus()).thenReturn(OrderItemStatus.DELIVERED);
        lenient().when(oi.getProductItem()).thenReturn(pi);
        lenient().when(pi.getProduct()).thenReturn(product);
        lenient().when(product.getId()).thenReturn(10L);

        return oi;
    }

    @Test
    @DisplayName("리뷰 생성 성공")
    void create_success() {
        Long orderItemId = 1L;
        Long userId = 1L;

        OrderItem oi = deliveredOrderItem(orderItemId, userId);

        when(orderItemRepository.findForReviewById(orderItemId))
            .thenReturn(Optional.of(oi));
        when(reviewRepository.existsByOrderItem_Id(orderItemId))
            .thenReturn(false);
        when(reviewRepository.save(any(Review.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        CreateReviewRequest req = mock(CreateReviewRequest.class);
        when(req.getOrderItemId()).thenReturn(orderItemId);
        when(req.getRating()).thenReturn(5);
        when(req.getContent()).thenReturn("좋아요");

        reviewService.createReview(authUser(userId), req, List.of());

        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("리뷰 생성 실패 - 주문 없음")
    void create_fail_notFound() {
        when(orderItemRepository.findForReviewById(99L))
            .thenReturn(Optional.empty());

        CreateReviewRequest req = mock(CreateReviewRequest.class);
        when(req.getOrderItemId()).thenReturn(99L);

        assertThatThrownBy(() ->
            reviewService.createReview(authUser(1L), req, List.of())
        ).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("리뷰 수정 성공")
    void update_success() {
        Long reviewId = 1L;
        Long userId = 1L;

        Review rv = reviewOwnedBy(userId);

        when(reviewRepository.findById(reviewId))
            .thenReturn(Optional.of(rv));

        UpdateReviewRequest req = mock(UpdateReviewRequest.class);
        when(req.getRating()).thenReturn(4);
        when(req.getContent()).thenReturn("수정됨");
        when(req.getRetainedImageUrls()).thenReturn(List.of());

        reviewService.updateReview(authUser(userId), reviewId, req, null);

        verify(rv).update(4, "수정됨");
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 존재하지 않음")
    void update_fail_notFound() {
        when(reviewRepository.findById(99L))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            reviewService.updateReview(
                authUser(1L),
                99L,
                mock(UpdateReviewRequest.class),
                null
            )
        ).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("리뷰 수정 실패 - 삭제된 리뷰")
    void update_fail_deleted() {
        Review rv = mock(Review.class);
        when(rv.getStatus()).thenReturn(ReviewStatus.DELETED);

        when(reviewRepository.findById(1L))
            .thenReturn(Optional.of(rv));

        assertThatThrownBy(() ->
            reviewService.updateReview(
                authUser(1L),
                1L,
                mock(UpdateReviewRequest.class),
                null
            )
        ).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("리뷰 삭제 성공")
    void delete_success() {
        Long reviewId = 1L;
        Long userId = 1L;

        Review rv = reviewOwnedBy(userId);

        when(reviewRepository.findById(reviewId))
            .thenReturn(Optional.of(rv));

        reviewService.deleteReview(authUser(userId), reviewId);

        verify(rv).delete();
    }

    @Test
    @DisplayName("내 리뷰 조회 성공")
    void myReviews_success() {
        Long userId = 1L;
        Pageable pageable = mock(Pageable.class);

        when(reviewRepository.findAllByUser_IdAndStatus(
            userId, ReviewStatus.ACTIVE, pageable))
            .thenReturn(Page.empty());

        Page<?> result = reviewService.getMyReviews(authUser(userId), pageable);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("리뷰 가능 목록 조회 성공")
    void reviewable_success() {
        Long userId = 1L;

        when(orderItemRepository.findAllReviewableByUserId(userId))
            .thenReturn(List.of());

        var result = reviewService.getReviewable(authUser(userId));

        assertThat(result).isEmpty();
    }
}
