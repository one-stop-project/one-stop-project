package com.sparta.one_stop.domain.point.controller;

import com.sparta.one_stop.domain.point.dto.request.PointChargeRequest;
import com.sparta.one_stop.domain.point.dto.response.PointChargeResponse;
import com.sparta.one_stop.domain.point.service.PointService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬/테스트/개발 환경에서만 등록되는 테스트용 포인트 충전 API.
 *
 * <p>{@link Profile}을 컨트롤러 Bean에 적용하므로 prod 프로파일에서는
 * 해당 엔드포인트 자체가 Spring MVC에 등록되지 않는다.</p>
 */
@Profile({"local", "test", "dev"})
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/points")
public class PointChargeController {

    private final PointService pointService;

    @PostMapping("/charge")
    public ResponseEntity<ApiResponse<PointChargeResponse>> chargePoint(
        @AuthenticationPrincipal AuthUser authUser,
        @Valid @RequestBody PointChargeRequest request
    ) {
        PointChargeResponse response = pointService.chargePoint(
            authUser.userId(),
            request
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
