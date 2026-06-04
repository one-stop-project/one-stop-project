package com.sparta.one_stop.domain.admin.controller;

import com.sparta.one_stop.domain.admin.dto.AdminCouponCreateRequest;
import com.sparta.one_stop.domain.admin.dto.AdminCouponResponse;
import com.sparta.one_stop.domain.admin.service.AdminCouponService;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
import com.sparta.one_stop.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Coupon", description = "관리자 쿠폰 관리 API")
@RestController
@RequestMapping("/api/admin/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminCouponController {

    private final AdminCouponService adminCouponService;

    @Operation(summary = "쿠폰 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<AdminCouponResponse>> createCoupon(
        @RequestBody @Valid AdminCouponCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(adminCouponService.createCoupon(request)));
    }

    @Operation(summary = "쿠폰 목록 조회 (상태 필터 선택)")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminCouponResponse>>> getCoupons(
        @RequestParam(required = false) CouponStatus status,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminCouponService.getCoupons(status, pageable)));
    }

    @Operation(summary = "쿠폰 비활성화")
    @PatchMapping("/{couponId}/deactivate")
    public ResponseEntity<ApiResponse<AdminCouponResponse>> deactivateCoupon(
        @PathVariable Long couponId
    ) {
        return ResponseEntity.ok(ApiResponse.success(adminCouponService.deactivateCoupon(couponId)));
    }
}
