package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.auth.service.DeviceContextService;
import com.sparta.one_stop.domain.auth.service.DeviceLimitService;
import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import com.sparta.one_stop.global.enums.ratelimit.RateLimitPolicy;
import com.sparta.one_stop.global.ratelimit.RateLimitService;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import com.sparta.one_stop.global.util.ClientIpExtractor;
import com.sparta.one_stop.global.util.CookieUtil;
import jakarta.annotation.PostConstruct;
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
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OAuth2 로그인 성공 핸들러.
 *
 * <p>일반 로그인과 동일한 신규 기기 제한 및 기기 수명주기 정책을 적용한다.</p>
 * <ul>
 *   <li>기존 device_id 쿠키가 유효하면 재사용</li>
 *   <li>기기 등록 전 IP/계정 단위 Rate Limit 선검증</li>
 *   <li>기기 ZSET 등록 및 최대 기기 수 초과 시 LRU 추방</li>
 *   <li>Refresh Token 및 DeviceContext 저장</li>
 *   <li>추방된 기기의 Refresh Token 및 DeviceContext 제거</li>
 * </ul>
 *
 * <p>서버 테스트 환경에서는 교환 코드를 URL에 노출하지 않는다.
 * 프론트엔드 콜백에서 Access Token 교환이 필요한 경우에만
 * app.oauth2.expose-code-in-redirect=true로 명시적으로 활성화한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String DEVICE_ID_COOKIE = "device_id";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/auth";
    private static final String DEVICE_ID_COOKIE_PATH = "/";

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTokenService redisTokenService;
    private final DeviceLimitService deviceLimitService;
    private final DeviceContextService deviceContextService;
    private final RateLimitService rateLimitService;
    private final ClientIpExtractor clientIpExtractor;
    private final CookieUtil cookieUtil;

    @Value("${app.oauth2.success-redirect-uri:https://onestop1.duckdns.org/oauth2/success}")
    private String successRedirectUri;

    @Value("${app.oauth2.allowed-redirect-hosts:onestop1.duckdns.org}")
    private String allowedRedirectHosts;

    @Value("${app.oauth2.expose-code-in-redirect:false}")
    private boolean exposeCodeInRedirect;

    private Set<String> allowedHosts;

    @PostConstruct
    void validateRedirectConfiguration() {
        this.allowedHosts = Arrays.stream(allowedRedirectHosts.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .collect(Collectors.toUnmodifiableSet());

        validateRedirectUri(successRedirectUri, "app.oauth2.success-redirect-uri");
    }

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

        // 일반 로그인과 동일하게 실제 기기 등록 전에 제한을 검증한다.
        rateLimitService.tryConsume(RateLimitPolicy.DEVICE_REGISTER_PER_IP, clientIp);

        boolean isNewDevice = deviceLimitService.isNewDevice(user.getId(), deviceId);
        if (isNewDevice) {
            rateLimitService.tryConsume(
                RateLimitPolicy.DEVICE_REGISTER_PER_ACCOUNT,
                String.valueOf(user.getId())
            );
        }

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

        deviceContextService.bindContext(
            user.getId(),
            deviceId,
            userAgent,
            clientIp
        );

        cleanupEvictedDevice(user.getId(), registration.evictedDeviceId());
        addAuthenticationCookies(response, refreshToken, deviceId);

        String target = buildSuccessRedirectTarget(deviceId, accessToken);

        if (registration.failOpen()) {
            log.warn(
                "[OAuth2] Redis Fail-Open 상태로 로그인 완료: userId={}, deviceId={}",
                user.getId(),
                deviceId
            );
        }

        log.info(
            "[OAuth2] 로그인 성공: userId={}, deviceId={}, newDevice={}, evictedDeviceId={}, exposeCodeInRedirect={}",
            user.getId(),
            deviceId,
            registration.isNewDevice(),
            registration.evictedDeviceId(),
            exposeCodeInRedirect
        );

        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private String buildSuccessRedirectTarget(String deviceId, String accessToken) {
        validateRedirectUri(successRedirectUri, "app.oauth2.success-redirect-uri");

        if (!exposeCodeInRedirect) {
            return successRedirectUri;
        }

        String code = UUID.randomUUID().toString().replace("-", "");
        redisTokenService.saveOAuth2Code(code, deviceId, accessToken);

        return UriComponentsBuilder.fromUriString(successRedirectUri)
            .queryParam("code", code)
            .build()
            .toUriString();
    }

    private void validateRedirectUri(String redirectUri, String propertyName) {
        if (!StringUtils.hasText(redirectUri)) {
            throw new IllegalStateException(propertyName + " must not be blank");
        }

        URI uri;
        try {
            uri = URI.create(redirectUri);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(propertyName + " is not a valid URI", e);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException(propertyName + " must use https: " + redirectUri);
        }

        if (!allowedHosts.contains(uri.getHost())) {
            throw new IllegalStateException(
                propertyName + " host is not allowed: " + uri.getHost()
            );
        }
    }

    private void cleanupEvictedDevice(Long userId, String evictedDeviceId) {
        if (!StringUtils.hasText(evictedDeviceId)) {
            return;
        }

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
                REFRESH_TOKEN_COOKIE_PATH
            )
        );

        // OAuth2 callback(/login/oauth2/code/*)에서도 기존 device_id를 재사용할 수 있도록 Path=/ 적용.
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            cookieUtil.createHttpOnlyCookie(
                DEVICE_ID_COOKIE,
                deviceId,
                refreshTokenTtl,
                DEVICE_ID_COOKIE_PATH
            )
        );
    }

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
