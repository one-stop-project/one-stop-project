package com.sparta.one_stop.domain.point.controller;

import com.sparta.one_stop.domain.point.dto.request.PointChargeRequest;
import com.sparta.one_stop.domain.point.dto.response.PointChargeResponse;
import com.sparta.one_stop.domain.point.dto.response.PointResponse;
import com.sparta.one_stop.domain.point.service.PointService;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/points")
public class PointController {

    private final PointService pointService;

    // 내 포인트 잔액 + 이력 조회
    @GetMapping
    public ResponseEntity<ApiResponse<PointResponse>> getMyPoints(
        @AuthenticationPrincipal AuthUser authUser,
        @RequestParam(required = false) PointHistoryType type,
        @PageableDefault(
            size = 20,
            sort = "createdAt",
            direction = Sort.Direction.DESC
        ) Pageable pageable
    ) {
        Long userId = authUser.userId();

        PointResponse response = pointService.getMyPoints(
            userId,
            type,
            pageable
        );

        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }

    // 테스트용 포인트 충전
    @PostMapping("/charge")
    public ResponseEntity<ApiResponse<PointChargeResponse>> chargePoint(
        @AuthenticationPrincipal AuthUser authUser,
        @Valid @RequestBody PointChargeRequest request
    ) {
        Long userId = authUser.userId();

        PointChargeResponse response = pointService.chargePoint(
            userId,
            request
        );

        return ResponseEntity.ok(
            ApiResponse.success(response)
        );
    }

}
