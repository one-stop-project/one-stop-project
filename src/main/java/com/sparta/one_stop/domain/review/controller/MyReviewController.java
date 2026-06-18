package com.sparta.one_stop.domain.review.controller;

import com.sparta.one_stop.domain.review.dto.response.ReviewResponse;
import com.sparta.one_stop.domain.review.dto.response.ReviewableOrderItemResponse;
import com.sparta.one_stop.domain.review.service.ReviewService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class MyReviewController {

    private final ReviewService reviewService;

    @GetMapping("/reviews")
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
