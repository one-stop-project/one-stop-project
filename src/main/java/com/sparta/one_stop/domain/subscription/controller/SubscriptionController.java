package com.sparta.one_stop.domain.subscription.controller;

import com.sparta.one_stop.domain.subscription.dto.request.CancelSubscriptionRequest;
import com.sparta.one_stop.domain.subscription.dto.response.SubscriptionResponse;
import com.sparta.one_stop.domain.subscription.service.SubscriptionService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * 구독 시작
     */
    @PostMapping
    public ApiResponse<SubscriptionResponse> subscribe(
        @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.success(
            subscriptionService.subscribe(authUser.userId())
        );
    }

    /**
     * 내 구독 조회
     */
    @GetMapping("/me")
    public ApiResponse<SubscriptionResponse> getMySubscription(
        @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.success(
            subscriptionService.getMySubscription(authUser.userId())
        );
    }

    /**
     * 구독 해지
     */
    @PatchMapping("/cancel")
    public ApiResponse<SubscriptionResponse> cancelSubscription(
        @AuthenticationPrincipal AuthUser authUser,
        @Valid @RequestBody CancelSubscriptionRequest request
    ) {
        return ApiResponse.success(
            subscriptionService.cancelSubscription(
                authUser.userId(),
                request
            )
        );
    }
}
