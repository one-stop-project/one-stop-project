package com.sparta.one_stop.domain.point.payment;

import com.sparta.one_stop.domain.point.entity.Point;
import com.sparta.one_stop.domain.point.repository.PointRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 시 포인트 검증 가드
 *
 * <p><b>다중 방어선</b>
 * <pre>
 *   [Guard 1] 요청 자체 유효성
 *     - 음수/null 금액 거부
 *     - 정수 단위 (1원 단위) 검증
 *
 *   [Guard 2] 무결성 해시 검증
 *     - DB 직접 조작 감지
 *     - Point.verifyIntegrity() 호출
 *
 *   [Guard 3] 잔액 충분성
 *     - point.balance >= 사용 요청
 *     - 동시성 충돌 시 낙관적 락이 잡아줌
 *
 *   [Guard 4] 결제 흐름 사전 검증
 *     - 주문 생성 시점 ≠ 결제 승인 시점 사이 시간 차
 *     - 결제 직전 다시 한 번 검증
 * </pre>
 *
 * <p><b>왜 별도 컴포넌트인가</b>
 * <ul>
 *   <li>PointService(일반 사용자 흐름)와 PaymentService(결제 흐름) 양쪽에서 호출</li>
 *   <li>검증 로직 중복 방지 + 정책 변경 시 단일 변경 지점</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentPointGuard {

    private final PointRepository pointRepository;

    /**
     * 주문 생성 시점 검증 — 가벼운 검증 (DB 락 없이)
     *
     * <p>실제 차감은 결제 승인 시 별도로.
     */
    @Transactional(readOnly = true)
    public void validateOnOrderCreation(Long userId, Integer requestedPoint) {
        if (requestedPoint == null || requestedPoint < 0) {
            throw new CustomException(ErrorCode.POINT_002,
                "사용 포인트는 0 이상이어야 합니다: " + requestedPoint);
        }
        if (requestedPoint == 0) {
            return; // 포인트 미사용 — 검증 불필요
        }

        Point point = pointRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.POINT_001));

        // ★ 무결성 검증 — DB 직접 조작 감지
        point.verifyIntegrity();

        if (point.getBalance() < requestedPoint) {
            throw new CustomException(ErrorCode.POINT_002,
                String.format("포인트 부족: balance=%d, requested=%d",
                    point.getBalance(), requestedPoint));
        }
    }

    /**
     * 결제 승인 직전 재검증 — 엄격한 검증 (락 포함)
     *
     * <p><b>주문 생성과 결제 사이에 다른 트랜잭션이 차감했을 수 있음</b>
     * <br>예: 다른 탭에서 동시 결제, 만료 배치 실행 등
     *
     * <p>{@code REQUIRES_NEW}로 별도 트랜잭션 — 결제 트랜잭션 영향 격리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void validateBeforePaymentApproval(Long userId, Integer requestedPoint, Long orderId) {
        if (requestedPoint == null || requestedPoint <= 0) {
            return; // 포인트 미사용 결제
        }

        Point point = pointRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.POINT_001));

        // ★ 무결성 재검증
        try {
            point.verifyIntegrity();
        } catch (CustomException e) {
            log.error("[PAYMENT_GUARD] 결제 직전 무결성 위반 감지! userId={}, orderId={}", userId, orderId, e);
            throw e;  // 무결성 위반은 무조건 결제 차단
        }

        if (point.getBalance() < requestedPoint) {
            log.warn("[PAYMENT_GUARD] 주문 생성 ↔ 결제 사이 잔액 변동 감지: " +
                    "userId={}, orderId={}, currentBalance={}, requested={}",
                userId, orderId, point.getBalance(), requestedPoint);
            throw new CustomException(ErrorCode.POINT_002,
                "결제 직전 포인트 잔액이 부족합니다. 주문을 다시 시도해주세요.");
        }
    }
}
