package com.sparta.one_stop.domain.delivery.controller;

import com.sparta.one_stop.domain.delivery.dto.response.DeliveryHistoryResponse;
import com.sparta.one_stop.domain.delivery.dto.response.DeliveryResponse;
import com.sparta.one_stop.domain.delivery.service.DeliveryService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
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

    @GetMapping("/orders/{orderId}/deliveries")
    public ResponseEntity<ApiResponse<List<DeliveryResponse>>> getDeliveries(
        @PathVariable Long orderId,
        @AuthenticationPrincipal AuthUser authUser) {

        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.getDeliveries(orderId, authUser.userId())
            )
        );
    }

    @GetMapping("/deliveries/{deliveryId}/history")
    public ResponseEntity<ApiResponse<DeliveryHistoryResponse>> getDeliveryHistory(
        @PathVariable Long deliveryId,
        @AuthenticationPrincipal AuthUser authUser) {

        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.getDeliveryHistory(deliveryId, authUser.userId())
            )
        );
    }
}
