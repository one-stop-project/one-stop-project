package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.auth.service.DeviceContextService;
import com.sparta.one_stop.domain.auth.service.DeviceLimitService;
import com.sparta.one_stop.domain.auth.service.RedisTokenService;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.user.UserRole;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RedisTokenService redisTokenService;
    @Mock DeviceLimitService deviceLimitService;
    @Mock DeviceContextService deviceContextService;
    @Mock ClientIpExtractor clientIpExtractor;
    @Mock CookieUtil cookieUtil;
    @Mock Authentication authentication;

    private OAuth2SuccessHandler handler;
    private User user;

    @BeforeEach
    void setUp() {
        handler = new OAuth2SuccessHandler(jwtTokenProvider, redisTokenService, deviceLimitService,
            deviceContextService, clientIpExtractor, cookieUtil);
        ReflectionTestUtils.setField(handler, "redirectBase", "http://localhost:3001");

        user = User.builder().email("oauth@test.com").password("encoded").name("oauth")
            .role(UserRole.BUYER).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        CustomOAuth2User principal = new CustomOAuth2User(user, Map.of(), mock(OAuth2UserInfo.class));
        when(authentication.getPrincipal()).thenReturn(principal);
        when(jwtTokenProvider.createAccessToken(1L, UserRole.BUYER, 0)).thenReturn("access");
        when(jwtTokenProvider.getRefreshTokenExpirySeconds()).thenReturn(604800L);
        when(clientIpExtractor.extract(org.mockito.ArgumentMatchers.any())).thenReturn("127.0.0.1");
        when(cookieUtil.createHttpOnlyCookie(anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), anyString()))
            .thenAnswer(inv -> inv.getArgument(0) + "=" + inv.getArgument(1));
    }

    @Test
    void registers_device_context_and_reuses_existing_device_cookie() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("device_id", deviceId));
        request.addHeader("User-Agent", "test-agent");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenProvider.createRefreshToken(1L, deviceId)).thenReturn("refresh");
        when(deviceLimitService.registerDevice(1L, deviceId))
            .thenReturn(new DeviceLimitService.DeviceRegistrationResult(false, null, 1, false));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(deviceContextService).bindContext(1L, deviceId, "test-agent", "127.0.0.1");
        verify(redisTokenService).saveRefreshToken(1L, deviceId, "refresh", 604800L);
        verify(redisTokenService).saveOAuth2Code(anyString(), org.mockito.ArgumentMatchers.eq(deviceId), org.mockito.ArgumentMatchers.eq("access"));
        assertThat(response.getRedirectedUrl()).startsWith("http://localhost:3001/oauth2/callback?code=");
    }

    @Test
    void removes_refresh_token_and_context_for_lru_evicted_device() throws Exception {
        String deviceId = UUID.randomUUID().toString();
        String evicted = "old-device";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("device_id", deviceId));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtTokenProvider.createRefreshToken(1L, deviceId)).thenReturn("refresh");
        when(deviceLimitService.registerDevice(1L, deviceId))
            .thenReturn(new DeviceLimitService.DeviceRegistrationResult(true, evicted, 5, false));

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(redisTokenService).deleteRefreshToken(1L, evicted);
        verify(deviceContextService).removeContext(1L, evicted);
    }
}
