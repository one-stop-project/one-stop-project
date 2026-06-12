package com.sparta.one_stop.domain.auth.controller;

import com.sparta.one_stop.domain.auth.dto.request.LoginRequest;
import com.sparta.one_stop.domain.auth.dto.request.SignUpRequest;
import com.sparta.one_stop.domain.auth.dto.request.TokenRefreshRequest;
import com.sparta.one_stop.domain.auth.dto.response.LoginResponse;
import com.sparta.one_stop.domain.auth.dto.response.SignUpResponse;
import com.sparta.one_stop.domain.auth.dto.response.TokenRefreshResponse;
import com.sparta.one_stop.domain.auth.dto.result.LoginResult;
import com.sparta.one_stop.domain.auth.dto.result.RefreshResult;
import com.sparta.one_stop.domain.auth.service.AuthService;
import com.sparta.one_stop.domain.cart.service.CartMergeService;
import com.sparta.one_stop.domain.cart.support.GuestCartCookieProvider;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.response.ApiResponse;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import com.sparta.one_stop.global.util.ClientIpExtractor;
import com.sparta.one_stop.global.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@Tag(name = "Auth", description = "인증/인가 관리 API (회원가입, 로그인, 토큰 재발급, 로그아웃)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtil cookieUtil;
    private final CartMergeService cartMergeService;
    private final GuestCartCookieProvider guestCartCookieProvider;
    private final ClientIpExtractor clientIpExtractor;

    @Operation(summary = "회원가입", description = "일반 구매자(BUYER) 및 판매자(SELLER) 회원가입을 처리합니다. 판매자 가입 시 상호명과 사업자번호가 필수입니다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignUpResponse>> signup(
        @Valid @RequestBody SignUpRequest request, HttpServletRequest servletRequest) {
        String clientIp = clientIpExtractor.extract(servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(authService.signup(request, clientIp)));
    }

    @Operation(
        summary = "로그인 (토큰 및 Device ID 발급)",
        description = "이메일과 비밀번호로 로그인합니다. 성공 시 Access Token은 JSON 응답으로 반환되며, refresh_token과 device_id는 HttpOnly 보안 쿠키로 설정됩니다. guest_cart_id 쿠키가 있는 경우 비로그인 장바구니를 로그인 사용자의 DB 장바구니로 병합합니다."
    )
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest,
        @Parameter(in = ParameterIn.COOKIE, name = "device_id", description = "기존에 발급받은 기기 식별자 (없는 경우 서버에서 자동 생성)")
        @CookieValue(value = "device_id", required = false) String existingDeviceId,
        @Parameter(in = ParameterIn.COOKIE, name = GuestCartCookieProvider.GUEST_CART_COOKIE_NAME, description = "비로그인 장바구니 식별자")
        @CookieValue(value = GuestCartCookieProvider.GUEST_CART_COOKIE_NAME, required = false) String guestCartId
    ) {
        String clientIp = clientIpExtractor.extract(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");

        // 1. 서버 기반 Device ID 발급 (최초 로그인 시)
        String deviceId = (existingDeviceId != null) ? existingDeviceId : UUID.randomUUID().toString();

        // 2. 비즈니스 로직 처리
        LoginResult result = authService.login(request, deviceId, userAgent, clientIp);

        boolean guestCartMerged = false;

        // 3. 비로그인 장바구니 merge
        // guest_cart_id 쿠키가 있는 경우 Redis 장바구니를 userId 기준 DB 장바구니로 병합
        // 사용자 편의를 위해 merge 실패가 로그인 실패로 이어지지 않도록 처리
        try {
            guestCartMerged = cartMergeService.mergeGuestCartToUserCart(
                result.response().userId(),
                guestCartId
            );
        } catch (Exception e) {
            log.warn(
                "비로그인 장바구니 merge 실패 - 로그인은 정상 처리: userId={}",
                result.response().userId(),
                e
            );
        }

        // 4. 보안 쿠키 생성 (RT & Device ID)
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
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, rtCookie)
            .header(HttpHeaders.SET_COOKIE, deviceIdCookie);

        // 5. merge 완료 후 비로그인 장바구니 쿠키 삭제
        if (guestCartMerged) {
            String clearGuestCartCookie = guestCartCookieProvider.createExpiredCookie();

            responseBuilder.header(
                HttpHeaders.SET_COOKIE,
                clearGuestCartCookie
            );
        }

        return responseBuilder.body(
            ApiResponse.success(result.response())
        );
    }


    @Operation(
        summary = "토큰 재발급 (Refresh)",
        description = "브라우저 쿠키에 저장된 refresh_token과 device_id를 검증하여 새로운 Access Token을 발급하고, RTR(Rotation) 정책에 따라 Refresh Token을 갱신합니다."
    )
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(
        @Parameter(in = ParameterIn.COOKIE, name = "refresh_token", description = "인증을 위한 리프레시 토큰")
        @CookieValue(value = "refresh_token", required = false) String refreshToken,
        @Parameter(in = ParameterIn.COOKIE, name = "device_id", description = "다중 기기 검증을 위한 기기 식별자")
        @CookieValue(value = "device_id", required = false) String deviceId,
        HttpServletRequest servletRequest) {

        // ★ 쿠키 누락 시 500이 아니라 401(인증 필요)로 응답
        //   비로그인 사용자가 앱 진입 시 호출하는 정상 케이스이므로 ERROR 로그도 안 남김
        if (refreshToken == null || refreshToken.isBlank()
            || deviceId == null || deviceId.isBlank()) {
            throw new CustomException(ErrorCode.AUTH_010, "인증 정보가 없습니다. 다시 로그인해주세요.");
        }

        TokenRefreshRequest request = new TokenRefreshRequest(refreshToken);
        String userAgent = servletRequest.getHeader("User-Agent");
        String clientIp = clientIpExtractor.extract(servletRequest);
        RefreshResult result = authService.refresh(request, deviceId, userAgent, clientIp);

        String newRtCookie = cookieUtil.createHttpOnlyCookie(
            "refresh_token",
            result.newRefreshToken(),
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
        @Parameter(in = ParameterIn.COOKIE, name = "device_id", description = "로그아웃할 기기의 식별자")
        @CookieValue(value = "device_id", required = false) String deviceId,
        @Parameter(in = ParameterIn.HEADER, name = "Authorization", description = "Bearer {Access_Token} 형태의 인증 헤더")
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader, HttpServletRequest servletRequest) {

        String clientIp = clientIpExtractor.extract(servletRequest);
        authService.checkLogoutRateLimit(clientIp);

        // 1. 토큰 추출 위임 (캡슐화)
        String accessToken = jwtTokenProvider.resolveToken(authHeader);

        // 2. 서비스 로그아웃 처리(id 추출)
        Long userId = jwtTokenProvider.getUserIdAllowExpired(accessToken);

        // userId가 있으면 서버측 정리, 없어도 쿠키는 만료시킴 (best-effort)
        if (userId != null) {
            authService.logout(userId, deviceId, accessToken);
        } else {
            log.debug("logout — 유효한 토큰 없음, 쿠키만 만료 처리");
        }

        // 3. 브라우저 쿠키 강제 만료
        String clearRtCookie = cookieUtil.createExpiredCookie("refresh_token", "/api/auth");
        String clearDeviceCookie = cookieUtil.createExpiredCookie("device_id", "/api/auth");
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, clearRtCookie)
            .header(HttpHeaders.SET_COOKIE, clearDeviceCookie)
            .body(ApiResponse.success());
    }
}
