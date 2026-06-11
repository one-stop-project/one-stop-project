package com.sparta.one_stop.domain.order.service;

import com.sparta.one_stop.domain.order.dto.request.CancelOrderRequest;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderRequest;
import com.sparta.one_stop.domain.order.dto.response.CancelOrderResponse;
import com.sparta.one_stop.domain.order.dto.response.CreateOrderResponse;
import com.sparta.one_stop.domain.order.dto.response.OrderDetailResponse;
import com.sparta.one_stop.domain.order.dto.response.OrderPageResponse;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;

    // 주문 생성
    public CreateOrderResponse createOrder(
        Long userId,
        CreateOrderRequest request
    ) {
        return orderCommandService.createOrder(
            userId,
            request
        );
    }

    // 주문 목록 조회
    public OrderPageResponse getMyOrders(
        Long userId,
        OrderStatus status,
        LocalDate from,
        LocalDate to,
        int page,
        int size
    ) {
        return orderQueryService.getMyOrders(
            userId,
            status,
            from,
            to,
            page,
            size
        );
    }

    // 주문 단건 조회
    public OrderDetailResponse getOrder(
        Long userId,
        Long orderId
    ) {
        return orderQueryService.getOrder(
            userId,
            orderId
        );
    }

    // 주문 취소
    @Retryable(
        retryFor = org.springframework.orm.ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 500)
    )
    public CancelOrderResponse cancelOrder(
        Long userId,
        Long orderId,
        CancelOrderRequest request
    ) {
        return orderCommandService.cancelOrder(
            userId,
            orderId,
            request
        );
    }

    @Recover
    public CancelOrderResponse recoverCancelOrder(
        ObjectOptimisticLockingFailureException e,
        Long userId, Long orderId, CancelOrderRequest request) {
        throw new CustomException(ErrorCode.POINT_005);
    }


}
