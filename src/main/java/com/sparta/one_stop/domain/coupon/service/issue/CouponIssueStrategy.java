package com.sparta.one_stop.domain.coupon.service.issue;

import com.sparta.one_stop.domain.coupon.dto.response.IssueCouponResponse;

public interface CouponIssueStrategy {

    boolean supports(String strategyType);

    IssueCouponResponse issue(
        Long userId,
        Long couponId
    );
    
}
