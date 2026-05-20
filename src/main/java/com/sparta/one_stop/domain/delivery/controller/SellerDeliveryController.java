package com.sparta.one_stop.domain.delivery.controller;

import com.sparta.one_stop.domain.delivery.dto.request.RejectOrderRequest;
import com.sparta.one_stop.domain.delivery.dto.request.ShipDeliveryRequest;
import com.sparta.one_stop.domain.delivery.dto.request.UpdateDeliveryStatusRequest;
import com.sparta.one_stop.domain.delivery.dto.response.*;
import com.sparta.one_stop.domain.delivery.service.DeliveryService;
import com.sparta.one_stop.global.enums.OrderItemStatus;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller")
public class SellerDeliveryController {

    private final DeliveryService deliveryService;

    // 판매자 주문 목록 조회
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<Page<SellerOrderResponse>>> getSellerOrders(
        @RequestParam(required = false) OrderItemStatus status,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @PageableDefault(size = 20) Pageable pageable,
        @AuthenticationPrincipal CustomUserDetails userDetails) {

        // Seller 엔티티의 ID가 필요 — AuthUser에서 userId로 sellerId 조회는 Service에서 처리
        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.getSellerOrders(userDetails.getUserId(), status, pageable)
            )
        );
    }

    // 발주 확인 (ORDERED → CONFIRMED / ACCEPT → INSTRUCT)
    @PostMapping("/orders/{orderItemId}/confirm")
    public ResponseEntity<ApiResponse<ConfirmOrderResponse>> confirmOrder(
        @PathVariable Long orderItemId,
        @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.confirmOrder(orderItemId, userDetails.getUserId())
            )
        );
    }

    // 주문 거절 (ORDERED → REJECTED)
    @PostMapping("/orders/{orderItemId}/reject")
    public ResponseEntity<ApiResponse<RejectOrderResponse>> rejectOrder(
        @PathVariable Long orderItemId,
        @Valid @RequestBody RejectOrderRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.rejectOrder(orderItemId, userDetails.getUserId(), request)
            )
        );
    }

    // 운송장 등록 (INSTRUCT → DEPARTURE)
    @PostMapping("/deliveries/{deliveryId}/ship")
    public ResponseEntity<ApiResponse<ShipDeliveryResponse>> shipDelivery(
        @PathVariable Long deliveryId,
        @Valid @RequestBody ShipDeliveryRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.shipDelivery(deliveryId, userDetails.getUserId(), request)
            )
        );
    }

    // 배송 상태 변경 (DEPARTURE → DELIVERING → FINAL_DELIVERY)
    @PatchMapping("/deliveries/{deliveryId}/status")
    public ResponseEntity<ApiResponse<UpdateDeliveryStatusResponse>> updateDeliveryStatus(
        @PathVariable Long deliveryId,
        @Valid @RequestBody UpdateDeliveryStatusRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.updateDeliveryStatus(deliveryId, userDetails.getUserId(), request)
            )
        );
    }
}
