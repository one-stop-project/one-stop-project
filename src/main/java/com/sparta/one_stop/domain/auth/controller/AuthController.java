package com.sparta.one_stop.domain.auth.controller;

import com.sparta.one_stop.domain.auth.dto.request.LoginRequest;
import com.sparta.one_stop.domain.auth.dto.response.LoginResponse;
import com.sparta.one_stop.domain.auth.dto.result.LoginResult;
import com.sparta.one_stop.domain.auth.dto.result.RefreshResult;
import com.sparta.one_stop.domain.auth.dto.request.SignUpRequest;
import com.sparta.one_stop.domain.auth.dto.response.SignUpResponse;
import com.sparta.one_stop.domain.auth.dto.request.TokenRefreshRequest;
import com.sparta.one_stop.domain.auth.dto.response.TokenRefreshResponse;
import com.sparta.one_stop.domain.auth.service.AuthService;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.AuthUser;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import com.sparta.one_stop.global.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Auth", description = "인증/인가 관리 API (회원가입, 로그인, 토큰 재발급, 로그아웃)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtil cookieUtil;

    @Operation(summary = "회원가입", description = "일반 구매자(BUYER) 및 판매자(SELLER) 회원가입을 처리합니다. 판매자 가입 시 상호명과 사업자번호가 필수입니다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignUpResponse>> signup(
        @Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(authService.signup(request)));
    }

    @Operation(
        summary = "로그인 (토큰 및 Device ID 발급)",
        description = "이메일과 비밀번호로 로그인합니다. 성공 시 Access Token은 JSON 응답으로 반환되며, refresh_token과 device_id는 HttpOnly 보안 쿠키로 설정됩니다."
    )
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request,
        @Parameter(in = ParameterIn.COOKIE, name = "device_id", description = "기존에 발급받은 기기 식별자 (없는 경우 서버에서 자동 생성)")
        @CookieValue(value = "device_id", required = false) String existingDeviceId) {

        // 1. 서버 기반 Device ID 발급 (최초 로그인 시)
        String deviceId = (existingDeviceId != null) ? existingDeviceId : UUID.randomUUID().toString();

        // 2. 비즈니스 로직 처리
        LoginResult result = authService.login(request, deviceId);

        // 3. 보안 쿠키 생성 (RT & Device ID)
        String rtCookie = cookieUtil.createHttpOnlyCookie(
            "refresh_token",
            result.refreshToken(),
            jwtTokenProvider.getRefreshTokenExpirySeconds(),
            "/api/auth"
        );

        String deviceIdCookie = cookieUtil.createHttpOnlyCookie(
            "device_id", deviceId,
            jwtTokenProvider.getRefreshTokenExpirySeconds(),
            "/api/auth"
        );
        // ※ 주의: 프론트엔드 보안 강화를 위해 클라이언트에 내려가는 LoginResponse JSON에서
        // RefreshToken 필드는 null로 비우거나 아예 DTO에서 제거하는 것이 정석
        // 현재는 쿠키를 통해 안전하게 전달
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, rtCookie)
            .header(HttpHeaders.SET_COOKIE, deviceIdCookie)
            .body(ApiResponse.success(result.response()));
    }


    @Operation(
        summary = "토큰 재발급 (Refresh)",
        description = "브라우저 쿠키에 저장된 refresh_token과 device_id를 검증하여 새로운 Access Token을 발급하고, RTR(Rotation) 정책에 따라 Refresh Token을 갱신합니다."
    )
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
        @Parameter(in = ParameterIn.COOKIE, name = "refresh_token", required = true, description = "인증을 위한 리프레시 토큰")
        @CookieValue(value = "refresh_token") String refreshToken,
        @Parameter(in = ParameterIn.COOKIE, name = "device_id", required = true, description = "다중 기기 검증을 위한 기기 식별자")
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

    @Operation(
        summary = "로그아웃",
        description = "현재 사용자의 Access Token을 블랙리스트에 등록하여 세션을 무효화하고, 브라우저의 refresh_token 및 device_id 쿠키를 강제 만료시킵니다."
    )
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
        @Parameter(hidden = true) @AuthenticationPrincipal AuthUser authUser,
        @Parameter(in = ParameterIn.COOKIE, name = "device_id", description = "로그아웃할 기기의 식별자")
        @CookieValue(value = "device_id", required = false) String deviceId,
        @Parameter(in = ParameterIn.HEADER, name = "Authorization", description = "Bearer {Access_Token} 형태의 인증 헤더")
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
