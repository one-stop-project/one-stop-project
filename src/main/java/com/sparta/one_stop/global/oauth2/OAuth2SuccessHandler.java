package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.auth.service.DeviceLimitService;
import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import com.sparta.one_stop.global.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTokenService redisTokenService;
    private final DeviceLimitService deviceLimitService;
    private final CookieUtil cookieUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        var user = principal.getUser();

        String deviceId = UUID.randomUUID().toString();
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), deviceId);

        // 🔴 소셜 로그인 유저도 완벽하게 기기 제한 인프라 편입 (보안 구멍 해소)
        deviceLimitService.registerDevice(user.getId(), deviceId);
        redisTokenService.saveRefreshToken(user.getId(), deviceId, refreshToken, jwtTokenProvider.getRefreshTokenExpirySeconds());

        String rtCookie = cookieUtil.createHttpOnlyCookie("refresh_token", refreshToken, jwtTokenProvider.getRefreshTokenExpirySeconds(), "/api/auth");
        String deviceIdCookie = cookieUtil.createHttpOnlyCookie("device_id", deviceId, jwtTokenProvider.getRefreshTokenExpirySeconds(), "/api/auth");

        response.addHeader(HttpHeaders.SET_COOKIE, rtCookie);
        response.addHeader(HttpHeaders.SET_COOKIE, deviceIdCookie);

        // 프론트엔드 Callback 주소로 Access Token 넘기며 리다이렉트
        String redirectUrl = "http://localhost:8080/oauth2/callback?token=" + accessToken;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
