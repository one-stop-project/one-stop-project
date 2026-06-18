package com.sparta.one_stop.domain.review.service;

import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.review.dto.request.CreateReviewRequest;
import com.sparta.one_stop.domain.review.dto.request.UpdateReviewRequest;
import com.sparta.one_stop.domain.review.dto.response.ReviewResponse;
import com.sparta.one_stop.domain.review.dto.response.ReviewableOrderItemResponse;
import com.sparta.one_stop.domain.review.entity.Review;
import com.sparta.one_stop.domain.review.entity.ReviewImage;
import com.sparta.one_stop.domain.review.event.ReviewCreatedEvent;
import com.sparta.one_stop.domain.review.event.ReviewSummaryRefreshEvent;
import com.sparta.one_stop.domain.review.repository.ReviewImageRepository;
import com.sparta.one_stop.domain.review.repository.ReviewRepository;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.review.ReviewStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.security.AuthUser;
import com.sparta.one_stop.global.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final OrderItemRepository orderItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageStorage imageStorage;

    /**
     * 리뷰 작성
     */
    @Transactional
    public ReviewResponse createReview(AuthUser authUser, CreateReviewRequest request, List<MultipartFile> images) {

        if (images != null && images.size() > 5) {
            throw new CustomException(ErrorCode.REVIEW_008);
        }

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

        Review review = Review.builder()
            .orderItem(orderItem)
            .product(orderItem.getProductItem().getProduct())
            .user(orderItem.getOrder().getUser())
            .rating(request.getRating())
            .content(request.getContent())
            .build();

        Review saved = reviewRepository.save(review);

        if (images != null) {
            int idx = 0;

            for (MultipartFile image : images) {

                byte[] bytes;
                try {
                    bytes = image.getBytes();
                } catch (java.io.IOException e) {
                    throw new CustomException(ErrorCode.COMMON_007);
                }

                String url = imageStorage.store(bytes, image.getContentType());

                reviewImageRepository.save(
                    ReviewImage.builder()
                        .review(saved)
                        .imageUrl(url)
                        .displayOrder(idx++)
                        .build()
                );
            }
        }

        try {
            eventPublisher.publishEvent(new ReviewCreatedEvent(saved.getProduct().getId(), saved.getId()));
        } catch (Exception e) {
            log.warn("[ReviewCreatedEvent] 이벤트 발행 실패 — 리뷰 저장에는 영향 없음: reviewId={}", saved.getId(), e);
        }

        return toResponse(saved);
    }

    /**
     * 리뷰 수정
     */
    @Transactional
    public ReviewResponse updateReview(AuthUser authUser, Long reviewId, UpdateReviewRequest request, List<MultipartFile> newImages)
    {
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_005));

        if (review.getStatus() == ReviewStatus.DELETED) {
            throw new CustomException(ErrorCode.REVIEW_005);
        }

        if (!review.getUser().getId().equals(authUser.userId())) {
            throw new CustomException(ErrorCode.REVIEW_006);
        }

        if (review.getCreatedAt().isBefore(LocalDateTime.now().minusDays(30))) {
            throw new CustomException(ErrorCode.REVIEW_007);
        }

        Long productId = review.getProduct().getId();

        // 리뷰 기본 정보 수정
        review.update(request.getRating(), request.getContent());

        // 기존 이미지 유지 목록 (null = 기존 유지)
        List<String> retained = request.getRetainedImageUrls();
        if (retained == null) {
            retained = review.getImages().stream()
                .map(ReviewImage::getImageUrl)
                .toList();
        }

        Set<String> retainedSet = new HashSet<>(retained);

        int retainedCount = retained.size();
        int newImageCount = newImages == null ? 0 : newImages.size();

        if (retainedCount + newImageCount > 5) {
            throw new CustomException(ErrorCode.REVIEW_008);
        }

        // 삭제 대상 이미지 S3 삭제
        List<String> deleteTargets = review.getImages().stream()
            .map(ReviewImage::getImageUrl)
            .filter(url -> !retainedSet.contains(url))
            .toList();

        deleteTargets.forEach(imageStorage::delete);

        // DB 이미지 제거
        review.getImages().removeIf(img -> !retainedSet.contains(img.getImageUrl()));

        // 순서 재정렬
        Map<String, ReviewImage> existingMap = review.getImages().stream()
            .collect(Collectors.toMap(ReviewImage::getImageUrl, img -> img));

        int idx = 0;
        for (String url : retained) {
            ReviewImage img = existingMap.get(url);
            if (img != null) {
                img.updateDisplayOrder(idx++);
            }
        }

        // 새 이미지 업로드 + 추가
        if (newImages != null && !newImages.isEmpty()) {
            for (MultipartFile file : newImages) {

                String url;
                try {
                    url = imageStorage.store(file.getBytes(), file.getContentType());
                } catch (Exception e) {
                    throw new CustomException(ErrorCode.COMMON_007);
                }

                review.getImages().add(
                    ReviewImage.builder()
                        .review(review)
                        .imageUrl(url)
                        .displayOrder(idx++)
                        .build()
                );
            }
        }

        // 리뷰 수정 후 상품 통계 갱신
        try {
            eventPublisher.publishEvent(new ReviewSummaryRefreshEvent(productId));
        } catch (Exception e) {
            log.warn("리뷰 요약 이벤트 실패: reviewId={}", reviewId, e);
        }

        return toResponse(review);
    }

    /**
     * 리뷰 삭제 — soft delete
     */
    @Transactional
    public void deleteReview(AuthUser authUser, Long reviewId) {

        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new CustomException(ErrorCode.REVIEW_005));

        if (review.getStatus() == ReviewStatus.DELETED) {
            throw new CustomException(ErrorCode.REVIEW_005);
        }

        if (!review.getUser().getId().equals(authUser.userId())) {
            throw new CustomException(ErrorCode.REVIEW_006);
        }

        Long productId = review.getProduct().getId();
        review.delete();

        try {
            eventPublisher.publishEvent(new ReviewSummaryRefreshEvent(productId));
        } catch (Exception e) {
            log.warn("[ReviewSummaryRefreshEvent] 이벤트 발행 실패 — 리뷰 삭제에는 영향 없음: reviewId={}", reviewId, e);
        }
    }

    /**
     * 내 리뷰 목록
     */
    public Page<ReviewResponse> getMyReviews(AuthUser authUser, Pageable pageable) {
        return reviewRepository.findAllByUser_IdAndStatus(authUser.userId(), ReviewStatus.ACTIVE, pageable)
            .map(this::toResponse);
    }

    /**
     * 리뷰 작성 가능 목록
     */
    public List<ReviewableOrderItemResponse> getReviewable(AuthUser authUser) {

        List<OrderItem> items =
            orderItemRepository.findAllReviewableByUserId(authUser.userId());

        if (items.isEmpty()) {
            return List.of();
        }

        Set<Long> reviewedOrderItemIds =
            reviewRepository.findReviewedOrderItemIds(
                    items.stream()
                        .map(OrderItem::getId)
                        .toList()
                )
                .stream()
                .collect(Collectors.toSet());

        return items.stream()
            .filter(i ->
                i.getStatus() == OrderItemStatus.DELIVERED
                    && !reviewedOrderItemIds.contains(i.getId())
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
            .orderItemId(review.getOrderItem().getId())
            .rating(review.getRating())
            .content(review.getContent())
            .images(
                review.getImages().stream()
                    .map(ReviewImage::getImageUrl)
                    .toList()
            )
            .createdAt(review.getCreatedAt())
            .updatedAt(review.getUpdatedAt())
            .build();
    }
}
