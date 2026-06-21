package com.sparta.one_stop.domain.seller.controller;

import com.sparta.one_stop.domain.seller.dto.response.SellerMyStatusResponse;
import com.sparta.one_stop.domain.seller.service.SellerMyService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller/me")
public class SellerMyController {

    private final SellerMyService sellerMyService;

    @Operation(summary = "내 판매자 상태 조회")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SellerMyStatusResponse>> getMySellerStatus(
        @AuthenticationPrincipal AuthUser authUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(sellerMyService.getMySellerStatus(authUser.userId())));
    }
}
