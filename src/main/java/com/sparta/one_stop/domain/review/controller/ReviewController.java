package com.sparta.one_stop.domain.review.controller;

import com.sparta.one_stop.domain.review.dto.request.CreateReviewRequest;
import com.sparta.one_stop.domain.review.dto.request.UpdateReviewRequest;
import com.sparta.one_stop.domain.review.dto.response.ReviewResponse;
import com.sparta.one_stop.domain.review.dto.response.ReviewableOrderItemResponse;
import com.sparta.one_stop.domain.review.service.ReviewService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ApiResponse<ReviewResponse> create(
        @AuthenticationPrincipal AuthUser authUser,
        @RequestBody CreateReviewRequest request
    ) {
        return ApiResponse.success(reviewService.createReview(authUser, request));
    }

    @PatchMapping("/{reviewId}")
    public ApiResponse<ReviewResponse> update(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long reviewId,
        @RequestBody UpdateReviewRequest request
    ) {
        return ApiResponse.success(reviewService.updateReview(authUser, reviewId, request));
    }

    @DeleteMapping("/{reviewId}")
    public ApiResponse<Void> delete(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long reviewId
    ) {
        reviewService.deleteReview(authUser, reviewId);
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<Page<ReviewResponse>> myReviews(
        @AuthenticationPrincipal AuthUser authUser,
        Pageable pageable
    ) {
        return ApiResponse.success(reviewService.getMyReviews(authUser, pageable));
    }

    @GetMapping("/reviewable")
    public ApiResponse<List<ReviewableOrderItemResponse>> reviewable(
        @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.success(reviewService.getReviewable(authUser));
    }
}
