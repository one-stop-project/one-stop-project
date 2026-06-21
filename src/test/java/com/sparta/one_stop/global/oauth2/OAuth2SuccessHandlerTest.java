package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.auth.service.DeviceContextService;
import com.sparta.one_stop.domain.auth.service.DeviceLimitService;
import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.ratelimit.RateLimitPolicy;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.ratelimit.RateLimitService;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import com.sparta.one_stop.global.util.ClientIpExtractor;
import com.sparta.one_stop.global.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    private static final Long USER_ID = 1L;
    private static final String ACCESS_TOKEN = "access";
    private static final String REFRESH_TOKEN = "refresh";
    private static final long REFRESH_TTL_SECONDS = 604_800L;
    private static final String CLIENT_IP = "127.0.0.1";
    private static final String USER_AGENT = "test-agent";

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RedisTokenService redisTokenService;

    @Mock
    private DeviceLimitService deviceLimitService;

    @Mock
    private DeviceContextService deviceContextService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private ClientIpExtractor clientIpExtractor;

    @Mock
    private CookieUtil cookieUtil;

    @Mock
    private Authentication authentication;

    private OAuth2SuccessHandler handler;
    private User user;

    @BeforeEach
    void setUp() {
        handler = new OAuth2SuccessHandler(
            jwtTokenProvider,
            redisTokenService,
            deviceLimitService,
            deviceContextService,
            rateLimitService,
            clientIpExtractor,
            cookieUtil
        );

        ReflectionTestUtils.setField(handler, "successRedirectUri", "https://onestop1.duckdns.org/oauth2/callback");
        ReflectionTestUtils.setField(handler, "allowedRedirectHosts", "onestop1.duckdns.org");
        ReflectionTestUtils.setField(handler, "exposeCodeInRedirect", true);

        user = User.builder()
            .email("oauth@test.com")
            .password("encoded")
            .name("oauth")
            .role(UserRole.BUYER)
            .build();
        ReflectionTestUtils.setField(user, "id", USER_ID);

        CustomOAuth2User principal = new CustomOAuth2User(
            user,
            Map.of(),
            mock(OAuth2UserInfo.class)
        );

        when(authentication.getPrincipal()).thenReturn(principal);
        when(jwtTokenProvider.createAccessToken(USER_ID, UserRole.BUYER, 0))
            .thenReturn(ACCESS_TOKEN);
        when(jwtTokenProvider.getRefreshTokenExpirySeconds())
            .thenReturn(REFRESH_TTL_SECONDS);
        when(clientIpExtractor.extract(any()))
            .thenReturn(CLIENT_IP);
        when(cookieUtil.createHttpOnlyCookie(anyString(), anyString(), anyLong(), anyString()))
            .thenAnswer(invocation -> invocation.getArgument(0) + "=" + invocation.getArgument(1));
    }

    @Test
    void registers_device_context_and_reuses_existing_device_cookie() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        MockHttpServletRequest request = requestWithDeviceCookie(deviceId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(deviceLimitService.isNewDevice(USER_ID, deviceId)).thenReturn(false);
        when(jwtTokenProvider.createRefreshToken(USER_ID, deviceId)).thenReturn(REFRESH_TOKEN);
        when(deviceLimitService.registerDevice(USER_ID, deviceId))
            .thenReturn(new DeviceLimitService.DeviceRegistrationResult(false, null, 1, false));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(rateLimitService).tryConsume(RateLimitPolicy.DEVICE_REGISTER_PER_IP, CLIENT_IP);
        verify(rateLimitService, never()).tryConsume(
            RateLimitPolicy.DEVICE_REGISTER_PER_ACCOUNT,
            String.valueOf(USER_ID)
        );
        verify(deviceContextService).bindContext(USER_ID, deviceId, USER_AGENT, CLIENT_IP);
        verify(redisTokenService).saveRefreshToken(
            USER_ID,
            deviceId,
            REFRESH_TOKEN,
            REFRESH_TTL_SECONDS
        );
        verify(redisTokenService).saveOAuth2Code(anyString(), eq(deviceId), eq(ACCESS_TOKEN));
        verify(cookieUtil).createHttpOnlyCookie(
            "refresh_token",
            REFRESH_TOKEN,
            REFRESH_TTL_SECONDS,
            "/api/auth"
        );
        verify(cookieUtil).createHttpOnlyCookie(
            "device_id",
            deviceId,
            REFRESH_TTL_SECONDS,
            "/"
        );

        assertThat(response.getRedirectedUrl())
            .startsWith("https://onestop1.duckdns.org/oauth2/callback?code=");
    }

    @Test
    void applies_account_rate_limit_before_registering_new_device() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        MockHttpServletRequest request = requestWithDeviceCookie(deviceId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(deviceLimitService.isNewDevice(USER_ID, deviceId)).thenReturn(true);
        when(jwtTokenProvider.createRefreshToken(USER_ID, deviceId)).thenReturn(REFRESH_TOKEN);
        when(deviceLimitService.registerDevice(USER_ID, deviceId))
            .thenReturn(new DeviceLimitService.DeviceRegistrationResult(true, null, 1, false));

        handler.onAuthenticationSuccess(request, response, authentication);

        var ordered = inOrder(rateLimitService, deviceLimitService);
        ordered.verify(rateLimitService)
            .tryConsume(RateLimitPolicy.DEVICE_REGISTER_PER_IP, CLIENT_IP);
        ordered.verify(deviceLimitService)
            .isNewDevice(USER_ID, deviceId);
        ordered.verify(rateLimitService)
            .tryConsume(RateLimitPolicy.DEVICE_REGISTER_PER_ACCOUNT, String.valueOf(USER_ID));
        ordered.verify(deviceLimitService)
            .registerDevice(USER_ID, deviceId);
    }

    @Test
    void removes_refresh_token_and_context_for_lru_evicted_device() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        String evictedDeviceId = "old-device";
        MockHttpServletRequest request = requestWithDeviceCookie(deviceId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(deviceLimitService.isNewDevice(USER_ID, deviceId)).thenReturn(true);
        when(jwtTokenProvider.createRefreshToken(USER_ID, deviceId)).thenReturn(REFRESH_TOKEN);
        when(deviceLimitService.registerDevice(USER_ID, deviceId))
            .thenReturn(new DeviceLimitService.DeviceRegistrationResult(
                true,
                evictedDeviceId,
                5,
                false
            ));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(redisTokenService).deleteRefreshToken(USER_ID, evictedDeviceId);
        verify(deviceContextService).removeContext(USER_ID, evictedDeviceId);
    }

    private MockHttpServletRequest requestWithDeviceCookie(String deviceId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("device_id", deviceId));
        request.addHeader("User-Agent", USER_AGENT);
        return request;
    }
}
