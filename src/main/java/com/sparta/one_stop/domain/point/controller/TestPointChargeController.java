package com.sparta.one_stop.domain.point.controller;

import com.sparta.one_stop.domain.point.dto.request.PointChargeRequest;
import com.sparta.one_stop.domain.point.dto.response.PointChargeResponse;
import com.sparta.one_stop.domain.point.service.PointService;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;

/**
 * 서버 부하 테스트/시연 전용 포인트 충전 API.
 *
 * 보안 정책:
 * - app.test-api.point-charge.enabled=true일 때만 Bean 등록
 * - JWT 인증 필요
 * - BUYER 권한 필요
 * - X-Test-Api-Key 헤더 필요
 * - 본인 포인트만 충전
 *
 * 운영 테스트 종료 후 반드시 enabled=false로 되돌린다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/test/users/me/points")
@ConditionalOnProperty(
    prefix = "app.test-api.point-charge",
    name = "enabled",
    havingValue = "true"
)
public class TestPointChargeController {

    private static final String TEST_API_KEY_HEADER = "X-Test-Api-Key";

    private final PointService pointService;

    @Value("${app.test-api.point-charge.api-key:}")
    private String testApiKey;

    @PostMapping("/charge")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<PointChargeResponse>> chargePointForTest(
        @AuthenticationPrincipal AuthUser authUser,
        @RequestHeader(value = TEST_API_KEY_HEADER, required = false) String requestApiKey,
        @Valid @RequestBody PointChargeRequest request
    ) {
        validateTestApiKey(requestApiKey);

        PointChargeResponse response = pointService.chargePoint(
            authUser.userId(),
            request
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private void validateTestApiKey(String requestApiKey) {
        if (!StringUtils.hasText(testApiKey) || !StringUtils.hasText(requestApiKey)) {
            throw new CustomException(ErrorCode.AUTH_011);
        }

        byte[] expected = testApiKey.getBytes();
        byte[] actual = requestApiKey.getBytes();

        if (!MessageDigest.isEqual(expected, actual)) {
            throw new CustomException(ErrorCode.AUTH_011);
        }
    }
}


