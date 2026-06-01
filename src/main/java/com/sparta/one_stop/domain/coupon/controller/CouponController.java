package com.sparta.one_stop.domain.coupon.controller;

import com.sparta.one_stop.domain.coupon.dto.response.AvailableCouponResponse;
import com.sparta.one_stop.domain.coupon.dto.response.IssueCouponResponse;
import com.sparta.one_stop.domain.coupon.dto.response.MyCouponPageResponse;
import com.sparta.one_stop.domain.coupon.service.CouponService;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CouponController {

    private final CouponService couponService;

    // 발급 가능 쿠폰 목록 조회
    @GetMapping("/coupons/available")
    public ResponseEntity<ApiResponse<List<AvailableCouponResponse>>> getAvailableCoupons() {
        List<AvailableCouponResponse> response = couponService.getAvailableCoupons();

        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }

    // 선착순 쿠폰 발급
    @PostMapping("/coupons/{couponId}/issue")
    public ResponseEntity<ApiResponse<IssueCouponResponse>> issueCoupon(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long couponId
    ) {
        Long userId = authUser.userId();

        IssueCouponResponse response = couponService.issueCoupon(
            userId,
            couponId
        );

        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }

    // 내 쿠폰 목록 조회
    @GetMapping("/users/me/coupons")
    public ResponseEntity<ApiResponse<MyCouponPageResponse>> getMyCoupons(
        @AuthenticationPrincipal AuthUser authUser,
        @RequestParam(required = false) UserCouponStatus status,
        @PageableDefault(
            size = 20,
            sort = "createdAt",
            direction = Sort.Direction.DESC
        ) Pageable pageable
    ) {
        Long userId = authUser.userId();

        MyCouponPageResponse response = couponService.getMyCoupons(
            userId,
            status,
            pageable
        );

        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }

}
