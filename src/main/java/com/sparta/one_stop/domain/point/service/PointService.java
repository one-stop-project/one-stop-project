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
        retryFor = {
            ObjectOptimisticLockingFailureException.class,
            org.springframework.dao.DataIntegrityViolationException.class  // 신규 유저 동시 첫 충전 시 user_id UNIQUE 위반 재시도
        },
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

    @Recover
    public PointChargeResponse recoverChargePointDive(
        org.springframework.dao.DataIntegrityViolationException e,
        Long userId, PointChargeRequest request) {

        // DIVE 재시도도 모두 실패 — 재시도 사이 다른 트랜잭션이 지갑을 만들었어야 정상인데
        // 여기까지 왔다면 일시적 충돌이 아닌 진짜 제약 위반
        log.error("[POINT_CHARGE] 지갑 생성 충돌 재시도 3회 실패 — userId={}", userId, e);
        throw new CustomException(ErrorCode.POINT_005);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  사용 — 결제 흐름의 핵심 (충돌 빈번)
    //  ★ 재시도는 결제(PaymentService) 레벨에서 수행한다.
    //    이유: usePoint는 결제 트랜잭션에 합류(REQUIRED)해야 원자성이 유지된다.
    //         (포인트만 차감되고 결제 실패하는 부분 커밋 방지)
    //    낙관락 충돌 시 결제 트랜잭션 전체가 롤백되고 결제 레벨에서 재시도된다.
    //    여기에 @Retryable을 두면 TX 안에서 재시도가 돌아 낙관락 예외가
    //    커밋 시점에 터지므로 무용 — 그래서 제거함.
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public void usePoint(Long userId, Order order, Integer usedPoint) {
        pointTxService.usePoint(userId, order, usedPoint);
    }

    // 낙관적 락 동시성 테스트용 직접 차감 (테스트 후 제거)
    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 5,
        backoff = @Backoff(delay = 30, multiplier = 2.0, maxDelay = 300)
    )
    public Integer deductPointForTest(Long userId, Integer amount) {
        return pointTxService.deductPointForTest(userId, amount);
    }

    @Recover
    public Integer recoverDeductForTest(ObjectOptimisticLockingFailureException e,
        Long userId, Integer amount) {
        log.error("[POINT_TEST] 낙관적 락 재시도 5회 실패 — userId={}, amount={}", userId, amount, e);
        throw new CustomException(ErrorCode.POINT_005);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  환불 — 주문 취소 (관리자/사용자 동시 호출 가능)
    //  ★ 재시도는 주문 취소(OrderCommandService) 레벨에서 수행한다.
    //    환불도 주문 취소 트랜잭션에 합류해야 원자성이 유지되므로 동일 정책.
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public Integer refundPointByOrder(Order order) {
        return pointTxService.refundPointByOrder(order);
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
