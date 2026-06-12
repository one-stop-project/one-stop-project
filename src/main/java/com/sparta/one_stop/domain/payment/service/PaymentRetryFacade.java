package com.sparta.one_stop.domain.payment.service;

import com.sparta.one_stop.domain.payment.dto.request.ApprovePaymentRequest;
import com.sparta.one_stop.domain.payment.dto.response.ApprovePaymentResponse;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * 결제 승인 재시도 퍼사드
 *
 * <p><b>역할</b>: 결제 트랜잭션 "전체"를 낙관적 락 충돌 시 재시도한다.
 *
 * <pre>
 *   [왜 별도 빈인가 — Self-Invocation 회피]
 *   PaymentService는 클래스 레벨 @Transactional이다. 만약 PaymentService에
 *   @Retryable을 직접 붙이면, 재시도가 트랜잭션 "안"에서 돌게 되어
 *   낙관적 락 예외가 메서드 리턴이 아닌 "커밋 시점"에 터진다 → 재시도 무용.
 *
 *   그래서 트랜잭션이 없는 이 퍼사드에서 @Retryable을 걸고,
 *   approvePayment() 호출마다 PaymentService의 트랜잭션이 새로 시작/커밋되게 한다.
 *
 *   [왜 결제 레벨 재시도인가 — 원자성]
 *   포인트 차감(usePoint)은 결제 트랜잭션에 합류(REQUIRED)해야 한다.
 *   포인트만 차감되고 결제가 실패하는 부분 커밋을 막기 위함.
 *   따라서 포인트에 REQUIRES_NEW를 주지 않고, 낙관락 충돌 시
 *   결제 트랜잭션 전체를 롤백 후 여기서 재시도한다 → 원자성 보장.
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRetryFacade {

    private final PaymentService paymentService;

    @Retryable(
        retryFor = ObjectOptimisticLockingFailureException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2.0, maxDelay = 500)
    )
    public ApprovePaymentResponse approvePayment(Long userId, ApprovePaymentRequest request) {
        return paymentService.approvePayment(userId, request);
    }

    @Recover
    public ApprovePaymentResponse recoverApprovePayment(
        ObjectOptimisticLockingFailureException e,
        Long userId, ApprovePaymentRequest request) {

        log.error("[PAYMENT] 결제 트랜잭션 낙관적 락 재시도 3회 모두 실패 — userId={}", userId, e);
        throw new CustomException(ErrorCode.PAYMENT_010);
    }

}
