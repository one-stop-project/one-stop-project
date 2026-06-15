package com.sparta.one_stop.domain.review.service;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.review.dto.request.CreateReviewRequest;
import com.sparta.one_stop.domain.review.dto.request.UpdateReviewRequest;
import com.sparta.one_stop.domain.review.entity.Review;
import com.sparta.one_stop.domain.review.entity.ReviewImage;
import com.sparta.one_stop.domain.review.repository.ReviewImageRepository;
import com.sparta.one_stop.domain.review.repository.ReviewRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.review.ReviewStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.security.AuthUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewImageRepository reviewImageRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReviewService reviewService;

    private AuthUser authUser(Long userId) {
        AuthUser au = mock(AuthUser.class);
        lenient().when(au.userId()).thenReturn(userId);
        return au;
    }

    private OrderItem deliveredOrderItem(Long orderItemId, Long userId) {
        OrderItem   oi      = mock(OrderItem.class);
        Order       ord     = mock(Order.class);
        User        user    = mock(User.class);
        ProductItem pi      = mock(ProductItem.class);
        Product     product = mock(Product.class);

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

    @Nested
    @DisplayName("리뷰 작성")
    class CreateReview {

        @Test
        @DisplayName("성공 - 배송 완료 상태")
        void success() {
            Long orderItemId = 1L;
            Long userId      = 1L;

            OrderItem oi = deliveredOrderItem(orderItemId, userId);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));
            when(reviewRepository.existsByOrderItem_Id(orderItemId)).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateReviewRequest req = mock(CreateReviewRequest.class);
            when(req.getOrderItemId()).thenReturn(orderItemId);
            when(req.getRating()).thenReturn(5);
            when(req.getContent()).thenReturn("배송 빠르고 품질 좋아요!");
            when(req.getImageUrls()).thenReturn(null);

            reviewService.createReview(authUser(userId), req);

            verify(reviewRepository).save(any(Review.class));
        }

        @Test
        @DisplayName("성공 - 이미지 포함(최대 5장)")
        void success_withImages() {
            Long orderItemId = 1L;
            Long userId      = 1L;

            OrderItem oi = deliveredOrderItem(orderItemId, userId);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));
            when(reviewRepository.existsByOrderItem_Id(orderItemId)).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateReviewRequest req = mock(CreateReviewRequest.class);
            when(req.getOrderItemId()).thenReturn(orderItemId);
            when(req.getRating()).thenReturn(4);
            when(req.getContent()).thenReturn("이미지와 함께 리뷰를 작성합니다.");
            when(req.getImageUrls()).thenReturn(List.of("url1", "url2", "url3", "url4", "url5"));

            reviewService.createReview(authUser(userId), req);

            verify(reviewImageRepository, times(5)).save(any(ReviewImage.class));
        }

        @Test
        @DisplayName("실패 - 주문 상품을 찾을 수 없음")
        void fail_orderItemNotFound() {
            when(orderItemRepository.findForReviewById(99L)).thenReturn(Optional.empty());

            CreateReviewRequest req = mock(CreateReviewRequest.class);
            when(req.getOrderItemId()).thenReturn(99L);

            assertThatThrownBy(() -> reviewService.createReview(authUser(1L), req))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("실패 - 본인 주문이 아님")
        void fail_notOwner() {
            Long orderItemId = 1L;
            OrderItem oi     = deliveredOrderItem(orderItemId, 999L);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));

            CreateReviewRequest req = mock(CreateReviewRequest.class);
            when(req.getOrderItemId()).thenReturn(orderItemId);

            assertThatThrownBy(() -> reviewService.createReview(authUser(1L), req))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("실패 - 배송 미완료(SHIPPING 상태)")
        void fail_notDelivered_shipping() {
            Long orderItemId = 1L;
            Long userId      = 1L;

            OrderItem oi   = mock(OrderItem.class);
            Order     ord  = mock(Order.class);
            User      user = mock(User.class);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));
            when(oi.getOrder()).thenReturn(ord);
            when(ord.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(userId);
            when(oi.getStatus()).thenReturn(OrderItemStatus.SHIPPING);

            CreateReviewRequest req = mock(CreateReviewRequest.class);
            when(req.getOrderItemId()).thenReturn(orderItemId);

            assertThatThrownBy(() -> reviewService.createReview(authUser(userId), req))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("실패 - CANCELLED 상태 주문")
        void fail_cancelledOrder() {
            Long orderItemId = 1L;
            Long userId      = 1L;

            OrderItem oi   = mock(OrderItem.class);
            Order     ord  = mock(Order.class);
            User      user = mock(User.class);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));
            when(oi.getOrder()).thenReturn(ord);
            when(ord.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(userId);
            when(oi.getStatus()).thenReturn(OrderItemStatus.CANCELLED);

            CreateReviewRequest req = mock(CreateReviewRequest.class);
            when(req.getOrderItemId()).thenReturn(orderItemId);

            assertThatThrownBy(() -> reviewService.createReview(authUser(userId), req))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("실패 - REJECTED 상태 주문")
        void fail_rejectedOrder() {
            Long orderItemId = 1L;
            Long userId      = 1L;

            OrderItem oi   = mock(OrderItem.class);
            Order     ord  = mock(Order.class);
            User      user = mock(User.class);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));
            when(oi.getOrder()).thenReturn(ord);
            when(ord.getUser()).thenReturn(user);
            when(user.getId()).thenReturn(userId);
            when(oi.getStatus()).thenReturn(OrderItemStatus.REJECTED);

            CreateReviewRequest req = mock(CreateReviewRequest.class);
            when(req.getOrderItemId()).thenReturn(orderItemId);

            assertThatThrownBy(() -> reviewService.createReview(authUser(userId), req))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("실패 - 동일 주문 상품 중복 리뷰")
        void fail_duplicateReview() {
            Long orderItemId = 1L;
            Long userId      = 1L;

            OrderItem oi = deliveredOrderItem(orderItemId, userId);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));
            when(reviewRepository.existsByOrderItem_Id(orderItemId)).thenReturn(true);

            CreateReviewRequest req = mock(CreateReviewRequest.class);
            when(req.getOrderItemId()).thenReturn(orderItemId);

            assertThatThrownBy(() -> reviewService.createReview(authUser(userId), req))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("리뷰 수정")
    class UpdateReview {

        private Review reviewOwnedBy(Long userId) {
            Review  rv      = mock(Review.class);
            User    user    = mock(User.class);
            Product product = mock(Product.class);
            lenient().when(rv.getUser()).thenReturn(user);
            lenient().when(user.getId()).thenReturn(userId);
            lenient().when(rv.getProduct()).thenReturn(product);
            lenient().when(product.getId()).thenReturn(10L);
            lenient().when(rv.getImages()).thenReturn(new java.util.ArrayList<>());
            lenient().when(rv.getCreatedAt()).thenReturn(LocalDateTime.now());
            // soft delete 되지 않은 상태
            lenient().when(rv.getStatus()).thenReturn(ReviewStatus.ACTIVE);
            return rv;
        }

        @Test
        @DisplayName("성공 - 작성 후 30일 이내 수정")
        void success() {
            Long reviewId = 1L;
            Long userId   = 1L;

            Review rv = reviewOwnedBy(userId);
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            UpdateReviewRequest req = mock(UpdateReviewRequest.class);
            when(req.getRating()).thenReturn(4);
            when(req.getContent()).thenReturn("수정된 리뷰 내용입니다");
            when(req.getImageUrls()).thenReturn(List.of()); // @NotNull이므로 빈 배열

            reviewService.updateReview(authUser(userId), reviewId, req);

            verify(rv).update(4, "수정된 리뷰 내용입니다");
            assertThat(rv.getImages()).isEmpty();
        }

        @Test
        @DisplayName("성공 - 이미지 교체")
        void success_replaceImages() {
            Long reviewId = 1L;
            Long userId   = 1L;

            Review rv = reviewOwnedBy(userId);
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            UpdateReviewRequest req = mock(UpdateReviewRequest.class);
            when(req.getRating()).thenReturn(5);
            when(req.getContent()).thenReturn("이미지도 교체해봅니다.");
            when(req.getImageUrls()).thenReturn(List.of("new1.jpg", "new2.jpg"));

            reviewService.updateReview(authUser(userId), reviewId, req);

            assertThat(rv.getImages()).hasSize(2);
            assertThat(rv.getImages())
                .extracting(ReviewImage::getImageUrl)
                .containsExactly("new1.jpg", "new2.jpg");
        }

        @Test
        @DisplayName("실패 - 리뷰를 찾을 수 없음")
        void fail_reviewNotFound() {
            when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                reviewService.updateReview(authUser(1L), 99L, mock(UpdateReviewRequest.class))
            ).isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("실패 - 이미 삭제된 리뷰")
        void fail_deletedReview() {
            Long reviewId = 1L;
            Long userId   = 1L;

            Review rv = mock(Review.class);
            when(rv.getStatus()).thenReturn(ReviewStatus.DELETED);
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            assertThatThrownBy(() ->
                reviewService.updateReview(authUser(userId), reviewId, mock(UpdateReviewRequest.class))
            ).isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("실패 - 본인 리뷰가 아님")
        void fail_notOwner() {
            Long reviewId = 1L;

            Review rv = reviewOwnedBy(999L);
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            assertThatThrownBy(() ->
                reviewService.updateReview(authUser(1L), reviewId, mock(UpdateReviewRequest.class))
            ).isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("실패 - 작성 후 30일 초과")
        void fail_expired30Days() {
            Long reviewId = 1L;
            Long userId   = 1L;

            Review rv = reviewOwnedBy(userId);
            lenient().when(rv.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(31));
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            assertThatThrownBy(() ->
                reviewService.updateReview(authUser(userId), reviewId, mock(UpdateReviewRequest.class))
            ).isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("경계값 - 30일 1초 전은 수정 불가")
        void fail_exactly30DaysAgo() {
            Long reviewId = 1L;
            Long userId   = 1L;

            Review rv = reviewOwnedBy(userId);
            lenient().when(rv.getCreatedAt()).thenReturn(LocalDateTime.now().minusDays(30).minusSeconds(1));
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            assertThatThrownBy(() ->
                reviewService.updateReview(authUser(userId), reviewId, mock(UpdateReviewRequest.class))
            ).isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("리뷰 삭제")
    class DeleteReview {

        private Review reviewOwnedBy(Long userId) {
            Review  rv      = mock(Review.class);
            User    user    = mock(User.class);
            Product product = mock(Product.class);
            lenient().when(rv.getUser()).thenReturn(user);
            lenient().when(user.getId()).thenReturn(userId);
            lenient().when(rv.getProduct()).thenReturn(product);
            lenient().when(product.getId()).thenReturn(10L);
            lenient().when(rv.getStatus()).thenReturn(ReviewStatus.ACTIVE);
            return rv;
        }

        @Test
        @DisplayName("성공 - soft delete 호출 확인")
        void success() {
            Long reviewId = 1L;
            Long userId   = 1L;

            Review rv = reviewOwnedBy(userId);
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            reviewService.deleteReview(authUser(userId), reviewId);

            // hard delete가 아닌 soft delete 확인
            verify(rv).delete();
            verify(reviewRepository, never()).delete(any());
        }

        @Test
        @DisplayName("실패 - 이미 삭제된 리뷰")
        void fail_alreadyDeleted() {
            Long reviewId = 1L;
            Long userId   = 1L;

            Review rv = mock(Review.class);
            when(rv.getStatus()).thenReturn(ReviewStatus.DELETED);
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            assertThatThrownBy(() -> reviewService.deleteReview(authUser(userId), reviewId))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("실패 - 리뷰를 찾을 수 없음")
        void fail_reviewNotFound() {
            when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.deleteReview(authUser(1L), 99L))
                .isInstanceOf(CustomException.class);
        }

        @Test
        @DisplayName("실패 - 본인 리뷰가 아님")
        void fail_notOwner() {
            Long reviewId = 1L;

            Review rv = reviewOwnedBy(999L);
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            assertThatThrownBy(() -> reviewService.deleteReview(authUser(1L), reviewId))
                .isInstanceOf(CustomException.class);
        }
    }

    @Nested
    @DisplayName("내 리뷰 목록 조회")
    class GetMyReviews {

        @Test
        @DisplayName("성공 - 빈 목록 반환")
        void success_empty() {
            Long     userId   = 1L;
            Pageable pageable = mock(Pageable.class);
            when(reviewRepository.findAllByUser_IdAndStatus(userId, ReviewStatus.ACTIVE, pageable))
                .thenReturn(Page.empty());

            Page<?> result = reviewService.getMyReviews(authUser(userId), pageable);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공 - 리뷰 목록 반환")
        void success_withReviews() {
            Long     userId   = 1L;
            Pageable pageable = mock(Pageable.class);

            Review  rv      = mock(Review.class);
            Product product = mock(Product.class);
            lenient().when(rv.getProduct()).thenReturn(product);
            lenient().when(product.getId()).thenReturn(10L);
            lenient().when(rv.getRating()).thenReturn(5);
            lenient().when(rv.getContent()).thenReturn("좋아요");
            lenient().when(rv.getImages()).thenReturn(List.of());

            when(reviewRepository.findAllByUser_IdAndStatus(userId, ReviewStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(rv)));

            Page<?> result = reviewService.getMyReviews(authUser(userId), pageable);

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("리뷰 작성 가능 목록 조회")
    class GetReviewable {

        @Test
        @DisplayName("성공 - DELIVERED + 미작성 항목만 반환")
        void success_onlyDeliveredAndNotReviewed() {
            Long userId      = 1L;
            Long orderItemId = 1L;

            OrderItem   oi      = mock(OrderItem.class);
            ProductItem pi      = mock(ProductItem.class);
            Product     product = mock(Product.class);
            when(oi.getId()).thenReturn(orderItemId);
            when(oi.getStatus()).thenReturn(OrderItemStatus.DELIVERED);
            when(oi.getProductItem()).thenReturn(pi);
            when(pi.getProduct()).thenReturn(product);
            when(product.getId()).thenReturn(10L);
            when(pi.getOptionSummary()).thenReturn("블랙/XL");
            when(oi.getItemName()).thenReturn("반팔티");
            when(oi.getUpdatedAt()).thenReturn(LocalDateTime.now());

            when(orderItemRepository.findAllReviewableByUserId(userId)).thenReturn(List.of(oi));
            when(reviewRepository.findReviewedOrderItemIds(List.of(orderItemId))).thenReturn(List.of());

            var result = reviewService.getReviewable(authUser(userId));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).orderItemId()).isEqualTo(orderItemId);
        }

        @Test
        @DisplayName("성공 - 이미 작성된 항목은 제외")
        void success_excludeAlreadyReviewed() {
            Long userId      = 1L;
            Long orderItemId = 1L;

            OrderItem oi = mock(OrderItem.class);
            when(oi.getId()).thenReturn(orderItemId);
            when(oi.getStatus()).thenReturn(OrderItemStatus.DELIVERED);

            when(orderItemRepository.findAllReviewableByUserId(userId)).thenReturn(List.of(oi));
            when(reviewRepository.findReviewedOrderItemIds(List.of(orderItemId)))
                .thenReturn(List.of(orderItemId));

            var result = reviewService.getReviewable(authUser(userId));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공 - 대상 주문 상품 없을 경우 빈 리스트 반환")
        void success_empty() {
            Long userId = 1L;
            when(orderItemRepository.findAllReviewableByUserId(userId)).thenReturn(List.of());

            var result = reviewService.getReviewable(authUser(userId));

            assertThat(result).isEmpty();
            // 방어코드로 인해 findReviewedOrderItemIds 호출 자체가 없어야 함
            verify(reviewRepository, never()).findReviewedOrderItemIds(anyList());
        }

        @Test
        @DisplayName("성공 - DELIVERED가 아닌 항목(SHIPPING)은 제외")
        void success_excludeNonDelivered() {
            Long userId      = 1L;
            Long orderItemId = 1L;

            OrderItem oi = mock(OrderItem.class);
            when(oi.getId()).thenReturn(orderItemId);
            when(oi.getStatus()).thenReturn(OrderItemStatus.SHIPPING);

            when(orderItemRepository.findAllReviewableByUserId(userId)).thenReturn(List.of(oi));
            when(reviewRepository.findReviewedOrderItemIds(List.of(orderItemId))).thenReturn(List.of());

            var result = reviewService.getReviewable(authUser(userId));

            assertThat(result).isEmpty();
        }
    }
}
