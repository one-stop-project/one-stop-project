package com.sparta.one_stop.domain.user.controller;

import com.sparta.one_stop.domain.user.dto.request.PasswordChangeRequest;
import com.sparta.one_stop.domain.user.dto.request.UserUpdateRequest;
import com.sparta.one_stop.domain.user.dto.request.WithdrawRequest;
import com.sparta.one_stop.domain.user.dto.response.UserMeResponse;
import com.sparta.one_stop.domain.user.dto.response.UserUpdateResponse;
import com.sparta.one_stop.domain.user.service.UserService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "User", description = "회원 본인 정보 관리 API")
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<UserMeResponse>> getMyInfo(
        @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(
            ApiResponse.success(userService.getMyInfo(authUser.userId())));
    }

    @Operation(summary = "내 정보 수정")
    @PatchMapping
    public ResponseEntity<ApiResponse<UserUpdateResponse>> updateMyInfo(
        @AuthenticationPrincipal AuthUser authUser,
        @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(
            ApiResponse.success(userService.updateMyInfo(authUser.userId(), request)));
    }

    @Operation(summary = "비밀번호 변경 — 변경 시 모든 기기 강제 로그아웃")
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
        @AuthenticationPrincipal AuthUser authUser,
        @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(authUser.userId(), request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "회원 탈퇴 — Soft Delete + 모든 기기 RT 삭제")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> withdraw(
        @AuthenticationPrincipal AuthUser authUser,
        @Valid @RequestBody WithdrawRequest request) {
        userService.withdraw(authUser.userId(), request);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
