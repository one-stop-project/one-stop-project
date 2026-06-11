package com.sparta.one_stop.domain.order.service;

import com.sparta.one_stop.domain.order.dto.request.CancelOrderRequest;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderRequest;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderItemRequest;
import com.sparta.one_stop.domain.order.dto.response.CancelOrderResponse;
import com.sparta.one_stop.domain.order.dto.response.CreateOrderResponse;
import com.sparta.one_stop.domain.order.dto.response.OrderDetailResponse;
import com.sparta.one_stop.domain.order.dto.response.OrderPageResponse;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.order.OrderType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String ORDER_LOCK_KEY_PREFIX = "lock:order:item:";
    private static final long LOCK_WAIT_TIME  = 3L;
    private static final long LOCK_LEASE_TIME = 5L;

    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;
    private final RedissonClient redissonClient;

    public CreateOrderResponse createOrder(Long userId, CreateOrderRequest request) {
        return orderCommandService.createOrder(userId, request);
    }

    /** Redis 분산 락 기반 주문 생성 (테스트용 — 성능/정합성 비교) */
    public CreateOrderResponse createOrderWithRedisLock(Long userId, CreateOrderRequest request) {
        List<Long> itemIds = getItemIds(request);

        List<RLock> locks = new ArrayList<>();
        try {
            for (Long itemId : itemIds) {
                RLock lock = redissonClient.getLock(ORDER_LOCK_KEY_PREFIX + itemId);
                boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
                if (!acquired) {
                    throw new CustomException(ErrorCode.ORDER_013);
                }
                locks.add(lock);
            }
            return orderCommandService.createOrderNoLock(userId, request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.ORDER_013);
        } finally {
            for (int i = locks.size() - 1; i >= 0; i--) {
                RLock lock = locks.get(i);
                try {
                    if (lock.isHeldByCurrentThread()) lock.unlock();
                } catch (Exception ignored) {}
            }
        }
    }

    private List<Long> getItemIds(CreateOrderRequest request) {
        if (request.orderType() == OrderType.DIRECT && request.items() != null) {
            return request.items().stream()
                .map(CreateOrderItemRequest::itemId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        }
        return List.of();
    }

    public OrderPageResponse getMyOrders(Long userId, OrderStatus status,
        LocalDate from, LocalDate to, int page, int size) {
        return orderQueryService.getMyOrders(userId, status, from, to, page, size);
    }

    public OrderDetailResponse getOrder(Long userId, Long orderId) {
        return orderQueryService.getOrder(userId, orderId);
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
