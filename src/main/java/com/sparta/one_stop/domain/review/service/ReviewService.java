package com.sparta.one_stop.domain.review.service;

import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.review.dto.request.CreateReviewRequest;
import com.sparta.one_stop.domain.review.dto.request.UpdateReviewRequest;
import com.sparta.one_stop.domain.review.dto.response.ReviewResponse;
import com.sparta.one_stop.domain.review.dto.response.ReviewableOrderItemResponse;
import com.sparta.one_stop.domain.review.entity.Review;
import com.sparta.one_stop.domain.review.entity.ReviewImage;
import com.sparta.one_stop.domain.review.repository.ReviewImageRepository;
import com.sparta.one_stop.domain.review.repository.ReviewRepository;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * 리뷰 작성
     */
    @Transactional
    public ReviewResponse createReview(AuthUser authUser, CreateReviewRequest request) {

        OrderItem orderItem = orderItemRepository.findForReviewById(request.getOrderItemId())
            .orElseThrow(() -> new CustomException(ErrorCode.ORDER_006));

        if (!orderItem.getOrder().getUser().getId().equals(authUser.userId())) {
            throw new CustomException(ErrorCode.REVIEW_006);
        }

        if (orderItem.getStatus() == OrderItemStatus.CANCELLED ||
            orderItem.getStatus() == OrderItemStatus.REJECTED) {
            throw new CustomException(ErrorCode.REVIEW_001);
        }

        if (orderItem.getStatus() != OrderItemStatus.DELIVERED) {
            throw new CustomException(ErrorCode.REVIEW_001);
        }

        if (reviewRepository.existsByOrderItem_Id(orderItem.getId())) {
            throw new CustomException(ErrorCode.REVIEW_002);
        }

        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new CustomException(ErrorCode.REVIEW_003);
        }

        if (request.getContent() != null &&
            (request.getContent().length() < 10 || request.getContent().length() > 1000)) {
            throw new CustomException(ErrorCode.REVIEW_004);
        }

        Review review = Review.builder()
            .orderItem(orderItem)
            .product(orderItem.getProductItem().getProduct())
            .user(orderItem.getOrder().getUser())
            .rating(request.getRating())
            .content(request.getContent())
            .build();

        Review saved = reviewRepository.save(review);

        if (request.getImageUrls() != null) {
            int idx = 0;
            for (String url : request.getImageUrls()) {
                reviewImageRepository.save(
                    ReviewImage.builder()
                        .review(saved)
                        .imageUrl(url)
                        .displayOrder(idx++)
                        .build()
                );
            }
        }

        return toResponse(saved);
    }

    /**
     * 리뷰 수정
     */
    @Transactional
    public ReviewResponse updateReview(AuthUser authUser, Long reviewId, UpdateReviewRequest request) {

        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_005));

        if (!review.getUser().getId().equals(authUser.userId())) {
            throw new CustomException(ErrorCode.REVIEW_006);
        }

        if (review.getCreatedAt().isBefore(LocalDateTime.now().minusDays(30))) {
            throw new CustomException(ErrorCode.REVIEW_007);
        }

        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new CustomException(ErrorCode.REVIEW_003);
        }

        if (request.getContent() != null &&
            (request.getContent().length() < 10 || request.getContent().length() > 1000)) {
            throw new CustomException(ErrorCode.REVIEW_004);
        }

        review.update(request.getRating(), request.getContent());

        reviewImageRepository.deleteAll(review.getImages());

        if (request.getNewImageUrls() != null) {
            int idx = 0;
            for (String url : request.getNewImageUrls()) {
                reviewImageRepository.save(
                    ReviewImage.builder()
                        .review(review)
                        .imageUrl(url)
                        .displayOrder(idx++)
                        .build()
                );
            }
        }

        return toResponse(review);
    }

    /**
     * 리뷰 삭제
     */
    @Transactional
    public void deleteReview(AuthUser authUser, Long reviewId) {

        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_005));

        if (!review.getUser().getId().equals(authUser.userId())) {
            throw new CustomException(ErrorCode.REVIEW_006);
        }

        reviewRepository.delete(review);
    }

    /**
     * 내 리뷰 목록
     */
    public Page<ReviewResponse> getMyReviews(AuthUser authUser, Pageable pageable) {
        return reviewRepository.findAllByUser_Id(authUser.userId(), pageable)
            .map(this::toResponse);
    }

    /**
     * 리뷰 작성 가능 목록
     */
    public List<ReviewableOrderItemResponse> getReviewable(AuthUser authUser) {

        List<OrderItem> items =
            orderItemRepository.findAllReviewableByUserId(authUser.userId());

        return items.stream()
            .filter(i ->
                i.getStatus() == OrderItemStatus.DELIVERED
                    && !reviewRepository.existsByOrderItem_Id(i.getId())
            )
            .map(i -> new ReviewableOrderItemResponse(
                i.getId(),
                i.getProductItem().getProduct().getId(),
                i.getProductItem().getOptionSummary(),
                i.getItemName(),
                i.getUpdatedAt()
            ))
            .toList();
    }

    private ReviewResponse toResponse(Review review) {
        return ReviewResponse.builder()
            .reviewId(review.getId())
            .productId(review.getProduct().getId())
            .rating(review.getRating())
            .content(review.getContent())
            .imageUrls(
                review.getImages().stream()
                    .map(ReviewImage::getImageUrl)
                    .toList()
            )
            .createdAt(review.getCreatedAt())
            .updatedAt(review.getUpdatedAt())
            .build();
    }
}
