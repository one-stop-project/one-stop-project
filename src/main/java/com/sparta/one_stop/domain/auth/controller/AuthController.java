package com.sparta.one_stop.domain.auth.controller;

import com.sparta.one_stop.domain.auth.dto.LoginRequest;
import com.sparta.one_stop.domain.auth.dto.LoginResponse;
import com.sparta.one_stop.domain.auth.dto.RefreshResult;
import com.sparta.one_stop.domain.auth.dto.SignUpRequest;
import com.sparta.one_stop.domain.auth.dto.SignUpResponse;
import com.sparta.one_stop.domain.auth.dto.TokenRefreshRequest;
import com.sparta.one_stop.domain.auth.dto.TokenRefreshResponse;
import com.sparta.one_stop.domain.auth.service.AuthService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import com.sparta.one_stop.global.util.CookieUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtil cookieUtil;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignUpResponse>> signup(
        @Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(authService.signup(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request,
        @CookieValue(value = "device_id", required = false) String existingDeviceId) {

        // 1. 서버 기반 Device ID 발급 (최초 로그인 시)
        String deviceId = (existingDeviceId != null) ? existingDeviceId : UUID.randomUUID().toString();

        // 2. 비즈니스 로직 처리
        LoginResponse responseDto = authService.login(request, deviceId);

        // 3. 보안 쿠키 생성 (RT & Device ID)
        String rtCookie = cookieUtil.createHttpOnlyCookie(
            "refresh_token", responseDto.refreshToken(), jwtTokenProvider.getRefreshTokenExpirySeconds(),
            "/api/auth"
        );

        String deviceIdCookie = cookieUtil.createHttpOnlyCookie(
            "device_id", deviceId, jwtTokenProvider.getRefreshTokenExpirySeconds(),
            "/api/auth"
        );
        // ※ 주의: 프론트엔드 보안 강화를 위해 클라이언트에 내려가는 LoginResponse JSON에서
        // RefreshToken 필드는 null로 비우거나 아예 DTO에서 제거하는 것이 정석
        // 현재는 쿠키를 통해 안전하게 전달
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, rtCookie)
            .header(HttpHeaders.SET_COOKIE, deviceIdCookie)
            .body(ApiResponse.success(responseDto));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
        @CookieValue(value = "refresh_token") String refreshToken,
        @CookieValue(value = "device_id") String deviceId) {

        // 1. 쿠키에서 읽은 값으로 TokenRefreshRequest DTO 생성 (또는 Service 파라미터 직접 전달)
        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);

        RefreshResult result = authService.refresh(request, deviceId);

        // 2. RTR(Rotation) 정책에 따라 발급된 새 RT를 쿠키에 덮어쓰기
        // responseDto.refreshToken() 추출 및 "/api/auth" 인자 추가
        String newRtCookie = cookieUtil.createHttpOnlyCookie(
            "refresh_token",
            result.newRefreshToken(),  // ← 여기에 새 RT
            jwtTokenProvider.getRefreshTokenExpirySeconds(),
            "/api/auth"
        );

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, newRtCookie)
            .body(ApiResponse.success(result.response()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
        @AuthenticationPrincipal AuthUser authUser,
        @CookieValue(value = "device_id", required = false) String deviceId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

        // 1. 토큰 추출 위임 (캡슐화)
        String accessToken = jwtTokenProvider.resolveToken(authHeader);

        // 2. 서비스 로그아웃 처리
        authService.logout(authUser.userId(), deviceId, accessToken);

        // 3. 브라우저 쿠키 강제 만료
        String clearRtCookie = cookieUtil.createExpiredCookie("refresh_token", "/api/auth");
        String clearDeviceCookie = cookieUtil.createExpiredCookie("device_id", "/api/auth");
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, clearRtCookie)
            .header(HttpHeaders.SET_COOKIE, clearDeviceCookie)
            .body(ApiResponse.success());
    }
}
