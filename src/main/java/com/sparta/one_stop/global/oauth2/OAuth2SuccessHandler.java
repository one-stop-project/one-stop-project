package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.auth.service.DeviceContextService;
import com.sparta.one_stop.domain.auth.service.DeviceLimitService;
import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import com.sparta.one_stop.global.util.ClientIpExtractor;
import com.sparta.one_stop.global.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

/**
 * OAuth2 로그인 성공 핸들러.
 *
 * <p>일반 로그인과 동일한 기기 정책을 적용한다.</p>
 * <ul>
 *   <li>기존 device_id 쿠키가 유효하면 재사용</li>
 *   <li>기기 ZSET 등록 및 최대 기기 수 초과 시 LRU 추방</li>
 *   <li>Refresh Token 저장</li>
 *   <li>DeviceContext 등록</li>
 *   <li>추방된 기기의 Refresh Token 및 DeviceContext 제거</li>
 * </ul>
 *
 * <p>Access Token은 URL에 노출하지 않고 1회용 교환 코드로 전달한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String DEVICE_ID_COOKIE = "device_id";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String AUTH_COOKIE_PATH = "/api/auth";

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTokenService redisTokenService;
    private final DeviceLimitService deviceLimitService;
    private final DeviceContextService deviceContextService;
    private final ClientIpExtractor clientIpExtractor;
    private final CookieUtil cookieUtil;

    @Value("${app.oauth2.redirect-base:http://localhost:3001}")
    private String redirectBase;

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException {
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        var user = principal.getUser();

        String deviceId = resolveDeviceId(request);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        String clientIp = clientIpExtractor.extract(request);

        String accessToken = jwtTokenProvider.createAccessToken(
            user.getId(),
            user.getRole(),
            user.getTokenVersion()
        );
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), deviceId);

        DeviceLimitService.DeviceRegistrationResult registration =
            deviceLimitService.registerDevice(user.getId(), deviceId);

        redisTokenService.saveRefreshToken(
            user.getId(),
            deviceId,
            refreshToken,
            jwtTokenProvider.getRefreshTokenExpirySeconds()
        );

        // Refresh 단계의 컨텍스트 검증 기준점을 OAuth2 로그인에서도 반드시 등록한다.
        deviceContextService.bindContext(
            user.getId(),
            deviceId,
            userAgent,
            clientIp
        );

        cleanupEvictedDevice(user.getId(), registration.evictedDeviceId());

        addAuthenticationCookies(response, refreshToken, deviceId);

        String code = UUID.randomUUID().toString().replace("-", "");
        redisTokenService.saveOAuth2Code(code, deviceId, accessToken);

        String target = UriComponentsBuilder.fromUriString(redirectBase)
            .path("/oauth2/callback")
            .queryParam("code", code)
            .build()
            .toUriString();

        if (registration.failOpen()) {
            log.warn(
                "[OAuth2] Redis Fail-Open 상태로 로그인 완료: userId={}, deviceId={}",
                user.getId(),
                deviceId
            );
        }

        log.info(
            "[OAuth2] 로그인 성공: userId={}, deviceId={}, newDevice={}, evictedDeviceId={}",
            user.getId(),
            deviceId,
            registration.isNewDevice(),
            registration.evictedDeviceId()
        );

        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private void cleanupEvictedDevice(Long userId, String evictedDeviceId) {
        if (!StringUtils.hasText(evictedDeviceId)) {
            return;
        }

        // ZSET에서는 이미 제거됐지만 RT와 기기 컨텍스트는 별도 키이므로 명시 정리한다.
        redisTokenService.deleteRefreshToken(userId, evictedDeviceId);
        deviceContextService.removeContext(userId, evictedDeviceId);
    }

    private void addAuthenticationCookies(
        HttpServletResponse response,
        String refreshToken,
        String deviceId
    ) {
        long refreshTokenTtl = jwtTokenProvider.getRefreshTokenExpirySeconds();

        response.addHeader(
            HttpHeaders.SET_COOKIE,
            cookieUtil.createHttpOnlyCookie(
                REFRESH_TOKEN_COOKIE,
                refreshToken,
                refreshTokenTtl,
                AUTH_COOKIE_PATH
            )
        );

        response.addHeader(
            HttpHeaders.SET_COOKIE,
            cookieUtil.createHttpOnlyCookie(
                DEVICE_ID_COOKIE,
                deviceId,
                refreshTokenTtl,
                AUTH_COOKIE_PATH
            )
        );
    }

    /**
     * 기존 device_id가 정상 UUID면 재사용한다.
     * OAuth2 로그인 때마다 새 ID를 만들면 동일 브라우저가 기기 슬롯을 계속 소비할 수 있다.
     */
    private String resolveDeviceId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (DEVICE_ID_COOKIE.equals(cookie.getName()) && isValidUuid(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return UUID.randomUUID().toString();
    }

    private boolean isValidUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
