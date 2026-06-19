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
import lombok.extern.slf4j.Slf4j;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 서버 부하 테스트/시연 전용 포인트 충전 API.
 *
 * <p>운영 상시 기능이 아니므로 다음 조건을 모두 만족해야 한다.</p>
 * <ul>
 *   <li>app.test-api.point-charge.enabled=true일 때만 Bean 등록</li>
 *   <li>JWT 인증 필요</li>
 *   <li>BUYER 권한 필요</li>
 *   <li>X-Test-Api-Key 헤더 필요</li>
 * </ul>
 *
 * <p>테스트 종료 후 app.test-api.point-charge.enabled=false로 반드시 되돌린다.</p>
 */
@Slf4j
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
    private String expectedApiKey;

    @PostMapping("/charge")
    @PreAuthorize("hasRole('BUYER')")
    public ResponseEntity<ApiResponse<PointChargeResponse>> chargePointForTest(
        @AuthenticationPrincipal AuthUser authUser,
        @RequestHeader(value = TEST_API_KEY_HEADER, required = false) String requestApiKey,
        @Valid @RequestBody PointChargeRequest request
    ) {
        validateTestApiKey(authUser, requestApiKey);

        PointChargeResponse response = pointService.chargePoint(
            authUser.userId(),
            request
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private void validateTestApiKey(AuthUser authUser, String requestApiKey) {
        if (!StringUtils.hasText(expectedApiKey) || !StringUtils.hasText(requestApiKey)) {
            log.warn(
                "[TEST_POINT_CHARGE] missing api key. userId={}",
                authUser != null ? authUser.userId() : null
            );
            throw new CustomException(ErrorCode.AUTH_007);
        }

        byte[] expected = expectedApiKey.getBytes(StandardCharsets.UTF_8);
        byte[] actual = requestApiKey.getBytes(StandardCharsets.UTF_8);

        if (!MessageDigest.isEqual(expected, actual)) {
            log.warn(
                "[TEST_POINT_CHARGE] invalid api key. userId={}",
                authUser != null ? authUser.userId() : null
            );
            throw new CustomException(ErrorCode.AUTH_007);
        }
    }
}
