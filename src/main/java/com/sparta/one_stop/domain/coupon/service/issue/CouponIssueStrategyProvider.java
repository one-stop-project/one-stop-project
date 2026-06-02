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

    public CouponIssueStrategy getStrategy() {
        return strategies.stream()
            .filter(strategy -> strategy.supports(strategyType))
            .findFirst()
            .orElseThrow(() -> new CustomException(ErrorCode.COMMON_008));
    }

}
