package com.sparta.one_stop.domain.payment.controller;

import com.sparta.one_stop.domain.payment.dto.request.ApprovePaymentRequest;
import com.sparta.one_stop.domain.payment.dto.response.ApprovePaymentResponse;
import com.sparta.one_stop.domain.payment.service.PaymentService;
import com.sparta.one_stop.global.enums.ratelimit.RateLimitPolicy;
import com.sparta.one_stop.global.ratelimit.RateLimitService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final RateLimitService rateLimitService;

    // 결제 승인 (2단계 Mock)
    @PostMapping
    public ResponseEntity<ApiResponse<ApprovePaymentResponse>> approvePayment(
        @AuthenticationPrincipal AuthUser authUser,
        @Valid @RequestBody ApprovePaymentRequest request
    ) {

        // Rate Limit 체크
        rateLimitService.tryConsume(
            RateLimitPolicy.PAYMENT_APPROVE_PER_USER,
            String.valueOf(authUser.userId())
        );

        ApprovePaymentResponse response = paymentService.approvePayment(
            authUser.userId(),
            request
        );

        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }

}
