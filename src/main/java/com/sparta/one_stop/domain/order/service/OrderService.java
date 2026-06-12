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

    public CreateOrderResponse createOrder(Long userId, CreateOrderRequest request) {
        return orderCommandService.createOrder(userId, request);
    }

    public OrderPageResponse getMyOrders(Long userId, OrderStatus status,
        LocalDate from, LocalDate to, int page, int size) {
        return orderQueryService.getMyOrders(userId, status, from, to, page, size);
    }

    public OrderDetailResponse getOrder(Long userId, Long orderId) {
        return orderQueryService.getOrder(userId, orderId);
    }

    /**
     * 주문 취소 재시도 정책.
     *
     * <p>주문 취소의 실제 상태 변경은 {@link OrderCommandService#cancelOrder(Long, Long, CancelOrderRequest)}
     * 에서 수행하며, 해당 메서드는 주문을 비관적 락으로 조회하여 동일 주문에 대한 중복 취소 및
     * 상태 변경 충돌을 방지한다.
     *
     * <p>다만 주문 취소 과정에서 호출되는 포인트 복구 로직
     * {@code pointService.refundPointByOrder(order)}는 Point 엔티티의 {@code @Version} 기반
     * 낙관적 락 충돌이 발생할 수 있다.
     *
     * <p>따라서 이 메서드의 {@code @Retryable(ObjectOptimisticLockingFailureException.class)}은
     * 주문 비관적 락을 재시도하기 위한 것이 아니라, 포인트 복구 중 발생할 수 있는
     * 낙관적 락 충돌을 상위 서비스 계층에서 재시도하기 위한 정책이다.
     */
    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
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
        throw new CustomException(ErrorCode.ORDER_013);
    }

}
