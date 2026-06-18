package com.sparta.one_stop.domain.review.service;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.review.dto.request.CreateReviewRequest;
import com.sparta.one_stop.domain.review.dto.request.UpdateReviewRequest;
import com.sparta.one_stop.domain.review.entity.Review;
import com.sparta.one_stop.domain.review.repository.ReviewImageRepository;
import com.sparta.one_stop.domain.review.repository.ReviewRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.review.ReviewStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.security.AuthUser;
import com.sparta.one_stop.global.storage.ImageStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
    @Mock private ReviewImageRepository reviewImageRepository;
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

    /**
     * 특정 userId 소유의 ACTIVE 리뷰를 만들되, createdAt을 파라미터로 지정 가능
     */
    private Review reviewOwnedBy(Long userId, LocalDateTime createdAt) {
        Review rv = mock(Review.class);
        User user = mock(User.class);
        Product product = mock(Product.class);
        OrderItem orderItem = mock(OrderItem.class);

        lenient().when(rv.getUser()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(userId);

        lenient().when(rv.getProduct()).thenReturn(product);
        lenient().when(product.getId()).thenReturn(10L);

        lenient().when(rv.getCreatedAt()).thenReturn(createdAt);
        lenient().when(rv.getStatus()).thenReturn(ReviewStatus.ACTIVE);
        lenient().when(rv.getImages()).thenReturn(new ArrayList<>());

        lenient().when(rv.getOrderItem()).thenReturn(orderItem);
        lenient().when(orderItem.getId()).thenReturn(1L);

        return rv;
    }

    private Review reviewOwnedBy(Long userId) {
        return reviewOwnedBy(userId, LocalDateTime.now());
    }

    /**
     * 특정 상태의 OrderItem을 만드는 헬퍼
     */
    private OrderItem orderItemWithStatus(Long orderItemId, Long userId, OrderItemStatus status) {
        OrderItem oi = mock(OrderItem.class);
        Order ord = mock(Order.class);
        User user = mock(User.class);
        ProductItem pi = mock(ProductItem.class);
        Product product = mock(Product.class);

        lenient().when(oi.getId()).thenReturn(orderItemId);
        lenient().when(oi.getOrder()).thenReturn(ord);
        lenient().when(ord.getUser()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(userId);
        lenient().when(oi.getStatus()).thenReturn(status);
        lenient().when(oi.getProductItem()).thenReturn(pi);
        lenient().when(pi.getProduct()).thenReturn(product);
        lenient().when(product.getId()).thenReturn(10L);

        return oi;
    }

    private OrderItem deliveredOrderItem(Long orderItemId, Long userId) {
        return orderItemWithStatus(orderItemId, userId, OrderItemStatus.DELIVERED);
    }

    private CreateReviewRequest createRequest(Long orderItemId) {
        CreateReviewRequest req = mock(CreateReviewRequest.class);
        lenient().when(req.getOrderItemId()).thenReturn(orderItemId);
        lenient().when(req.getRating()).thenReturn(5);
        lenient().when(req.getContent()).thenReturn("좋은 상품입니다 정말 추천합니다");
        return req;
    }

    @Nested
    @DisplayName("리뷰 생성")
    class CreateReview {

        @Test
        @DisplayName("리뷰 생성 성공 — DELIVERED, 미작성")
        void success() {
            Long orderItemId = 1L;
            Long userId = 1L;

            OrderItem oi = deliveredOrderItem(orderItemId, userId);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));
            when(reviewRepository.existsByOrderItem_Id(orderItemId)).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

            reviewService.createReview(authUser(userId), createRequest(orderItemId), List.of());

            verify(reviewRepository).save(any(Review.class));
        }

        @Test
        @DisplayName("리뷰 생성 성공 — 이미지 5장")
        void success_withImages() {
            Long orderItemId = 1L;
            Long userId = 1L;

            OrderItem oi = deliveredOrderItem(orderItemId, userId);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));
            when(reviewRepository.existsByOrderItem_Id(orderItemId)).thenReturn(false);
            when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
                Review saved = inv.getArgument(0);
                // getId()가 필요할 수 있으므로 mock 리턴
                Review spied = spy(saved);
                lenient().when(spied.getId()).thenReturn(100L);
                return spied;
            });
            when(imageStorage.store(any(byte[].class), any())).thenReturn("https://cdn.example.com/img.jpg");

            List<org.springframework.web.multipart.MultipartFile> images = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
                try {
                    lenient().when(file.getBytes()).thenReturn(new byte[]{1, 2, 3});
                    lenient().when(file.getContentType()).thenReturn("image/jpeg");
                } catch (Exception ignored) {}
                images.add(file);
            }

            reviewService.createReview(authUser(userId), createRequest(orderItemId), images);

            verify(reviewImageRepository, times(5)).save(any());
        }

        @Test
        @DisplayName("리뷰 생성 실패 — 주문 상품 없음 (ORDER_006)")
        void fail_orderItemNotFound() {
            when(orderItemRepository.findForReviewById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                reviewService.createReview(authUser(1L), createRequest(99L), List.of())
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.ORDER_006));
        }

        @Test
        @DisplayName("리뷰 생성 실패 — 타인 주문 (REVIEW_006)")
        void fail_notOwner() {
            Long orderItemId = 1L;
            Long ownerId = 1L;
            Long requesterId = 999L;

            OrderItem oi = deliveredOrderItem(orderItemId, ownerId);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));

            assertThatThrownBy(() ->
                reviewService.createReview(authUser(requesterId), createRequest(orderItemId), List.of())
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_006));
        }

        @Test
        @DisplayName("리뷰 생성 실패 — 미배송 SHIPPING 상태 (REVIEW_001)")
        void fail_notDelivered_shipping() {
            Long orderItemId = 1L;
            Long userId = 1L;

            OrderItem oi = orderItemWithStatus(orderItemId, userId, OrderItemStatus.SHIPPING);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));

            assertThatThrownBy(() ->
                reviewService.createReview(authUser(userId), createRequest(orderItemId), List.of())
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_001));
        }

        @Test
        @DisplayName("리뷰 생성 실패 — 취소 주문 CANCELLED (REVIEW_001)")
        void fail_cancelledOrder() {
            Long orderItemId = 1L;
            Long userId = 1L;

            OrderItem oi = orderItemWithStatus(orderItemId, userId, OrderItemStatus.CANCELLED);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));

            assertThatThrownBy(() ->
                reviewService.createReview(authUser(userId), createRequest(orderItemId), List.of())
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_001));
        }

        @Test
        @DisplayName("리뷰 생성 실패 — 거절 주문 REJECTED (REVIEW_001)")
        void fail_rejectedOrder() {
            Long orderItemId = 1L;
            Long userId = 1L;

            OrderItem oi = orderItemWithStatus(orderItemId, userId, OrderItemStatus.REJECTED);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));

            assertThatThrownBy(() ->
                reviewService.createReview(authUser(userId), createRequest(orderItemId), List.of())
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_001));
        }

        @Test
        @DisplayName("리뷰 생성 실패 — 중복 리뷰 (REVIEW_002)")
        void fail_duplicateReview() {
            Long orderItemId = 1L;
            Long userId = 1L;

            OrderItem oi = deliveredOrderItem(orderItemId, userId);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));
            when(reviewRepository.existsByOrderItem_Id(orderItemId)).thenReturn(true);

            assertThatThrownBy(() ->
                reviewService.createReview(authUser(userId), createRequest(orderItemId), List.of())
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_002));
        }

        @Test
        @DisplayName("리뷰 생성 실패 — 이미지 6장 초과 (REVIEW_008)")
        void fail_tooManyImages() {
            List<org.springframework.web.multipart.MultipartFile> images = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                images.add(mock(org.springframework.web.multipart.MultipartFile.class));
            }

            assertThatThrownBy(() ->
                reviewService.createReview(authUser(1L), createRequest(1L), images)
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_008));

            // 이미지 검증이 DB 조회보다 선행되므로 repository 호출 없음
            verify(orderItemRepository, never()).findForReviewById(any());
        }

        @Test
        @DisplayName("리뷰 생성 실패 — ORDERED 상태 (REVIEW_001)")
        void fail_notDelivered_ordered() {
            Long orderItemId = 1L;
            Long userId = 1L;

            OrderItem oi = orderItemWithStatus(orderItemId, userId, OrderItemStatus.ORDERED);
            when(orderItemRepository.findForReviewById(orderItemId)).thenReturn(Optional.of(oi));

            assertThatThrownBy(() ->
                reviewService.createReview(authUser(userId), createRequest(orderItemId), List.of())
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_001));
        }
    }

    @Nested
    @DisplayName("리뷰 수정")
    class UpdateReview {

        @Test
        @DisplayName("리뷰 수정 성공 — 30일 이내, 본인")
        void success() {
            Long reviewId = 1L;
            Long userId = 1L;

            Review rv = reviewOwnedBy(userId, LocalDateTime.now().minusDays(15));

            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            UpdateReviewRequest req = mock(UpdateReviewRequest.class);
            when(req.getRating()).thenReturn(4);
            when(req.getContent()).thenReturn("수정된 리뷰 내용입니다");
            when(req.getRetainedImageUrls()).thenReturn(List.of());

            reviewService.updateReview(authUser(userId), reviewId, req, null);

            verify(rv).update(4, "수정된 리뷰 내용입니다");
        }

        @Test
        @DisplayName("리뷰 수정 실패 — 30일 + 1초 경과 (REVIEW_007)")
        void fail_exactly30DaysAgo() {
            Long reviewId = 1L;
            Long userId = 1L;

            // 30일 + 1초 경과된 리뷰
            LocalDateTime past = LocalDateTime.now().minusDays(30).minusSeconds(1);
            Review rv = reviewOwnedBy(userId, past);

            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            UpdateReviewRequest req = mock(UpdateReviewRequest.class);

            assertThatThrownBy(() ->
                reviewService.updateReview(authUser(userId), reviewId, req, null)
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_007));

            verify(rv, never()).update(anyInt(), anyString());
        }

        @Test
        @DisplayName("리뷰 수정 실패 — 리뷰 존재하지 않음 (REVIEW_005)")
        void fail_notFound() {
            when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                reviewService.updateReview(authUser(1L), 99L, mock(UpdateReviewRequest.class), null)
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_005));
        }

        @Test
        @DisplayName("리뷰 수정 실패 — 삭제된 리뷰 (REVIEW_005)")
        void fail_deletedReview() {
            Review rv = mock(Review.class);
            when(rv.getStatus()).thenReturn(ReviewStatus.DELETED);

            when(reviewRepository.findById(1L)).thenReturn(Optional.of(rv));

            assertThatThrownBy(() ->
                reviewService.updateReview(authUser(1L), 1L, mock(UpdateReviewRequest.class), null)
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_005));
        }

        @Test
        @DisplayName("리뷰 수정 실패 — 타인 리뷰 (REVIEW_006)")
        void fail_notOwner() {
            Long reviewId = 1L;
            Long ownerId = 1L;
            Long requesterId = 999L;

            Review rv = reviewOwnedBy(ownerId, LocalDateTime.now());
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            UpdateReviewRequest req = mock(UpdateReviewRequest.class);

            assertThatThrownBy(() ->
                reviewService.updateReview(authUser(requesterId), reviewId, req, null)
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_006));

            verify(rv, never()).update(anyInt(), anyString());
        }

        @Test
        @DisplayName("리뷰 수정 — 29일 23시간 59분 (30일 미만 = 수정 가능)")
        void success_within30Days() {
            Long reviewId = 1L;
            Long userId = 1L;

            // 30일보다 1초 짧게 → isBefore(now - 30days) = false → 수정 가능
            // LocalDateTime.now() 호출 시점 차이를 감안하여 충분한 여유 확보
            LocalDateTime within30Days = LocalDateTime.now().minusDays(29).minusHours(23).minusMinutes(59);
            Review rv = reviewOwnedBy(userId, within30Days);

            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            UpdateReviewRequest req = mock(UpdateReviewRequest.class);
            when(req.getRating()).thenReturn(3);
            when(req.getContent()).thenReturn("경계값 수정");
            when(req.getRetainedImageUrls()).thenReturn(List.of());

            reviewService.updateReview(authUser(userId), reviewId, req, null);

            verify(rv).update(3, "경계값 수정");
        }
    }

    @Nested
    @DisplayName("리뷰 삭제")
    class DeleteReview {

        @Test
        @DisplayName("리뷰 삭제 성공 — ACTIVE, 본인")
        void success() {
            Long reviewId = 1L;
            Long userId = 1L;

            Review rv = reviewOwnedBy(userId);
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            reviewService.deleteReview(authUser(userId), reviewId);

            verify(rv).delete();
        }

        @Test
        @DisplayName("리뷰 삭제 실패 — 타인 리뷰 (REVIEW_006)")
        void fail_notOwner() {
            Long reviewId = 1L;
            Long ownerId = 1L;
            Long requesterId = 999L;

            Review rv = reviewOwnedBy(ownerId);
            when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(rv));

            assertThatThrownBy(() ->
                reviewService.deleteReview(authUser(requesterId), reviewId)
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_006));

            verify(rv, never()).delete();
        }

        @Test
        @DisplayName("리뷰 삭제 실패 — 이미 삭제된 리뷰 (REVIEW_005)")
        void fail_alreadyDeleted() {
            Review rv = mock(Review.class);
            when(rv.getStatus()).thenReturn(ReviewStatus.DELETED);

            when(reviewRepository.findById(1L)).thenReturn(Optional.of(rv));

            assertThatThrownBy(() ->
                reviewService.deleteReview(authUser(1L), 1L)
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_005));

            verify(rv, never()).delete();
        }

        @Test
        @DisplayName("리뷰 삭제 실패 — 리뷰 미존재 (REVIEW_005)")
        void fail_notFound() {
            when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                reviewService.deleteReview(authUser(1L), 99L)
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.REVIEW_005));
        }
    }

    @Nested
    @DisplayName("리뷰 조회")
    class QueryReview {

        @Test
        @DisplayName("내 리뷰 조회 성공")
        void myReviews_success() {
            Long userId = 1L;
            Pageable pageable = mock(Pageable.class);

            when(reviewRepository.findAllByUser_IdAndStatus(userId, ReviewStatus.ACTIVE, pageable))
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
}
