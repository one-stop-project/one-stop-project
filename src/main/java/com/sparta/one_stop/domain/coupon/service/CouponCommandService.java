package com.sparta.one_stop.domain.coupon.service;

import com.sparta.one_stop.domain.coupon.dto.CouponRestoreResult;
import com.sparta.one_stop.domain.coupon.dto.response.IssueCouponResponse;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.coupon.service.issue.CouponIssueStrategyProvider;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponCommandService {

    private final CouponIssueStrategyProvider couponIssueStrategyProvider;

    /**
     * 선착순 쿠폰 발급
     * - 설정된 쿠폰 발급 전략에 위임
     * - 기본 전략은 Redis DECR 기반 발급 방식
     */
    public IssueCouponResponse issueCoupon(
        Long userId,
        Long couponId
    ) {
        return couponIssueStrategyProvider.getStrategy()
            .issue(
                userId,
                couponId
            );
    }

    /**
     * 결제 성공 시 쿠폰 사용 처리
     * - 주문에 적용된 쿠폰이 없으면 처리하지 않음
     * - 결제 승인 성공 시점에 최종 사용 가능 여부를 검증
     * - UserCoupon 상태를 AVAILABLE → USED로 변경
     * - usedAt, usedOrder 저장
     */
    @Transactional
    public void useCouponByOrder(Order order) {
        if (order == null) {
            throw new CustomException(ErrorCode.ORDER_006);
        }

        UserCoupon userCoupon = order.getUserCoupon();

        if (userCoupon == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        userCoupon.validateUsable(
            order.getUser().getId(),
            now
        );

        userCoupon.use(
            order,
            now
        );
    }

    /**
     * 주문 취소 시 쿠폰 복구
     * - 주문에 적용된 쿠폰이 없으면 null 반환
     * - 결제 전 주문이면 쿠폰이 아직 USED 처리되지 않았으므로 복구하지 않음
     * - 쿠폰 만료 전이면 AVAILABLE로 복구
     * - 쿠폰 만료 후이면 EXPIRED로 변경
     */
    @Transactional
    public CouponRestoreResult restoreCouponByOrder(Order order) {
        if (order == null) {
            throw new CustomException(ErrorCode.ORDER_006);
        }

        UserCoupon userCoupon = order.getUserCoupon();

        if (userCoupon == null) {
            return null;
        }

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        userCoupon.restore(now);

        return new CouponRestoreResult(
            userCoupon.getId(),
            userCoupon.getCoupon().getName(),
            userCoupon.getStatus()
        );
    }

}
