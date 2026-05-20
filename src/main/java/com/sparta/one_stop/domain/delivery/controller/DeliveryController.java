package com.sparta.one_stop.domain.delivery.controller;


import com.sparta.one_stop.domain.delivery.dto.response.DeliveryHistoryResponse;
import com.sparta.one_stop.domain.delivery.dto.response.DeliveryResponse;
import com.sparta.one_stop.domain.delivery.service.DeliveryService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DeliveryController {

    private final DeliveryService deliveryService;

    // 구매자 배송 목록 조회
    @GetMapping("/orders/{orderId}/deliveries")
    public ResponseEntity<ApiResponse<List<DeliveryResponse>>> getDeliveries(
        @PathVariable Long orderId,
        @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.getDeliveries(orderId, userDetails.getUserId())
            )
        );
    }

    // 배송 이력 조회
    @GetMapping("/deliveries/{deliveryId}/history")
    public ResponseEntity<ApiResponse<DeliveryHistoryResponse>> getDeliveryHistory(
        @PathVariable Long deliveryId,
        @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.getDeliveryHistory(deliveryId, userDetails.getUserId())
            )
        );
    }
}
