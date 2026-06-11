package com.sparta.one_stop.domain.coupon.controller;

import com.sparta.one_stop.domain.coupon.dto.response.IssueCouponResponse;
import com.sparta.one_stop.domain.coupon.service.issue.CouponIssueStrategy;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쿠폰 발급 3전략 성능 비교용 테스트 컨트롤러 (테스트 후 제거)
 * decr / lua / lock 전략을 각 엔드포인트로 분리하여 k6 부하테스트 비교
 */
@RestController
@RequestMapping("/api/test/coupons")
public class CouponTestController {

    @Autowired
    @Qualifier("decrCouponIssueStrategy")
    private CouponIssueStrategy decrStrategy;

    @Autowired
    @Qualifier("luaCouponIssueStrategy")
    private CouponIssueStrategy luaStrategy;

    @Autowired
    @Qualifier("lockCouponIssueStrategy")
    private CouponIssueStrategy lockStrategy;

    @PostMapping("/{couponId}/issue/decr")
    public ResponseEntity<ApiResponse<IssueCouponResponse>> issueDecr(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long couponId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            decrStrategy.issue(authUser.userId(), couponId)
        ));
    }

    @PostMapping("/{couponId}/issue/lua")
    public ResponseEntity<ApiResponse<IssueCouponResponse>> issueLua(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long couponId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            luaStrategy.issue(authUser.userId(), couponId)
        ));
    }

    @PostMapping("/{couponId}/issue/lock")
    public ResponseEntity<ApiResponse<IssueCouponResponse>> issueLock(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long couponId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
            lockStrategy.issue(authUser.userId(), couponId)
        ));
    }
}
