package com.sparta.one_stop.domain.order.controller;

import com.sparta.one_stop.domain.order.dto.request.CancelOrderRequest;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderRequest;
import com.sparta.one_stop.domain.order.dto.response.CancelOrderResponse;
import com.sparta.one_stop.domain.order.dto.response.CreateOrderResponse;
import com.sparta.one_stop.domain.order.dto.response.OrderDetailResponse;
import com.sparta.one_stop.domain.order.dto.response.OrderPageResponse;
import com.sparta.one_stop.domain.order.service.OrderService;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    // 주문 생성 (1단계)
    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
        @AuthenticationPrincipal AuthUser authUser,
        @Valid @RequestBody CreateOrderRequest request
    ) {
        CreateOrderResponse response = orderService.createOrder(
            authUser.userId(),
            request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response));
    }

    // 내 주문 목록
    @GetMapping
    public ResponseEntity<ApiResponse<OrderPageResponse>> getMyOrders(
        @AuthenticationPrincipal AuthUser authUser,
        @RequestParam(required = false) OrderStatus status,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        OrderPageResponse response = orderService.getMyOrders(
            authUser.userId(),
            status,
            from,
            to,
            page,
            size
        );

        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }

    // 주문 단건 조회
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrder(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long orderId
    ) {
        OrderDetailResponse response = orderService.getOrder(
            authUser.userId(),
            orderId
        );

        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }

    // 주문 취소
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<CancelOrderResponse>> cancelOrder(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long orderId,
        @Valid @RequestBody CancelOrderRequest request
    ) {
        CancelOrderResponse response = orderService.cancelOrder(
            authUser.userId(),
            orderId,
            request
        );

        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }
}
