package com.sparta.one_stop.domain.review.controller;

import com.sparta.one_stop.domain.review.dto.request.CreateReviewRequest;
import com.sparta.one_stop.domain.review.dto.request.UpdateReviewRequest;
import com.sparta.one_stop.domain.review.dto.response.ReviewResponse;
import com.sparta.one_stop.domain.review.service.ReviewService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReviewResponse> create(
        @AuthenticationPrincipal AuthUser authUser,
        @Valid @RequestPart("request") CreateReviewRequest request,
        @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        return ApiResponse.success(
            reviewService.createReview(authUser, request, images)
        );
    }

    @PatchMapping(value = "/{reviewId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ReviewResponse> update(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long reviewId,
        @Valid @RequestPart("request") UpdateReviewRequest request,
        @RequestPart(value = "newImages", required = false) List<MultipartFile> newImages
    ) {
        return ApiResponse.success(
            reviewService.updateReview(authUser, reviewId, request, newImages)
        );
    }

    @DeleteMapping("/{reviewId}")
    public ApiResponse<Void> delete(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long reviewId
    ) {
        reviewService.deleteReview(authUser, reviewId);
        return ApiResponse.success();
    }
}
