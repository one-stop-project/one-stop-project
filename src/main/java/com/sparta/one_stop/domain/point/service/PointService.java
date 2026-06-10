package com.sparta.one_stop.domain.point.service;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.point.dto.request.PointChargeRequest;
import com.sparta.one_stop.domain.point.dto.response.PointChargeResponse;
import com.sparta.one_stop.domain.point.dto.response.PointResponse;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointService {

    private final PointTxService pointTxService;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  조회 — 재시도 불필요 (충돌 없음)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public PointResponse getMyPoints(Long userId, PointHistoryType type, Pageable pageable) {
        return pointTxService.getMyPoints(userId, type, pageable);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  충전 — 동시 변동 시 충돌 가능
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 500)
    )
    public PointChargeResponse chargePoint(Long userId, PointChargeRequest request) {
        return pointTxService.chargePoint(userId, request);
    }

    @Recover
    public PointChargeResponse recoverChargePoint(
        ObjectOptimisticLockingFailureException e,
        Long userId, PointChargeRequest request) {

        log.error("[POINT_CHARGE] 낙관적 락 재시도 3회 모두 실패 — userId={}, amount={}",
            userId, request != null ? request.amount() : null, e);

        throw new CustomException(ErrorCode.POINT_005);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  사용 — 결제 흐름의 핵심 (충돌 빈번)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 500)
    )
    public void usePoint(Long userId, Order order, Integer usedPoint) {
        pointTxService.usePoint(userId, order, usedPoint);
    }

    @Recover
    public void recoverUsePoint(
        ObjectOptimisticLockingFailureException e,
        Long userId, Order order, Integer usedPoint) {

        log.error("[POINT_USE] 낙관적 락 재시도 3회 모두 실패 — userId={}, orderId={}, amount={}",
            userId, order != null ? order.getId() : null, usedPoint, e);

        throw new CustomException(ErrorCode.POINT_005);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  환불 — 주문 취소 (관리자/사용자 동시 호출 가능)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 500)
    )
    public Integer refundPointByOrder(Order order) {
        return pointTxService.refundPointByOrder(order);
    }

    @Recover
    public Integer recoverRefundPointByOrder(
        ObjectOptimisticLockingFailureException e,
        Order order) {

        log.error("[POINT_REFUND] 낙관적 락 재시도 3회 모두 실패 — orderId={}",
            order != null ? order.getId() : null, e);

        throw new CustomException(ErrorCode.POINT_005);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  적립 — 배송 완료 시 (낙관적 락 충돌 가능)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 500)
    )
    public void earnPointByDelivery(Long orderId) {
        pointTxService.earnPointByDelivery(orderId);
    }

    @Recover
    public void recoverEarnPointByDelivery(
        ObjectOptimisticLockingFailureException e,
        Long orderId) {

        log.error("[POINT_EARN] 낙관적 락 재시도 3회 모두 실패 — orderId={}", orderId, e);
        throw new CustomException(ErrorCode.POINT_005);
    }
}
