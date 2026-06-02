package com.sparta.one_stop.domain.coupon.service.issue;

import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponIssueStrategyProvider {

    private final List<CouponIssueStrategy> strategies;

    @Value("${coupon.issue.strategy:decr}")
    private String strategyType;

    /**
     * 쿠폰 발급 전략 선택
     * - application 설정값 coupon.issue.strategy 기준으로 발급 전략을 선택한다
     * - 기본 전략은 Redis DECR 기반 decr 전략이다
     * - 지원 전략: decr / lua / lock
     */
    public CouponIssueStrategy getStrategy() {
        return strategies.stream()
            .filter(strategy -> strategy.supports(strategyType))
            .findFirst()
            .orElseThrow(() -> new CustomException(ErrorCode.COUPON_009));
    }

}
