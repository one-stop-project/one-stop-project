// ============ 수정 후 ============
package com.sparta.one_stop.domain.delivery.controller;

import com.sparta.one_stop.domain.delivery.dto.request.RejectOrderRequest;
import com.sparta.one_stop.domain.delivery.dto.request.ShipDeliveryRequest;
import com.sparta.one_stop.domain.delivery.dto.request.UpdateDeliveryStatusRequest;
import com.sparta.one_stop.domain.delivery.dto.response.ConfirmOrderResponse;
import com.sparta.one_stop.domain.delivery.dto.response.RejectOrderResponse;
import com.sparta.one_stop.domain.delivery.dto.response.SellerOrderResponse;
import com.sparta.one_stop.domain.delivery.dto.response.ShipDeliveryResponse;
import com.sparta.one_stop.domain.delivery.dto.response.UpdateDeliveryStatusResponse;
import com.sparta.one_stop.domain.delivery.service.DeliveryService;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
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

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<Page<SellerOrderResponse>>> getSellerOrders(
        @RequestParam(required = false) OrderItemStatus status,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @PageableDefault(size = 20) Pageable pageable,
        @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.getSellerOrders(authUser.userId(), status, pageable)
            )
        );
    }

    @PostMapping("/orders/{orderItemId}/confirm")
    public ResponseEntity<ApiResponse<ConfirmOrderResponse>> confirmOrder(
        @PathVariable Long orderItemId,
        @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.confirmOrder(orderItemId, authUser.userId())
            )
        );
    }

    @PostMapping("/orders/{orderItemId}/reject")
    public ResponseEntity<ApiResponse<RejectOrderResponse>> rejectOrder(
        @PathVariable Long orderItemId,
        @Valid @RequestBody RejectOrderRequest request,
        @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.rejectOrder(orderItemId, authUser.userId(), request)
            )
        );
    }

    @PostMapping("/deliveries/{deliveryId}/ship")
    public ResponseEntity<ApiResponse<ShipDeliveryResponse>> shipDelivery(
        @PathVariable Long deliveryId,
        @Valid @RequestBody ShipDeliveryRequest request,
        @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.shipDelivery(deliveryId, authUser.userId(), request)
            )
        );
    }

    @PatchMapping("/deliveries/{deliveryId}/status")
    public ResponseEntity<ApiResponse<UpdateDeliveryStatusResponse>> updateDeliveryStatus(
        @PathVariable Long deliveryId,
        @Valid @RequestBody UpdateDeliveryStatusRequest request,
        @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(
            ApiResponse.success(
                deliveryService.updateDeliveryStatus(deliveryId, authUser.userId(), request)
            )
        );
    }
}
