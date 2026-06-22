package com.sparta.one_stop.domain.auth.controller;

import com.sparta.one_stop.domain.auth.dto.request.LoginRequest;
import com.sparta.one_stop.domain.auth.dto.response.LoginResponse;
import com.sparta.one_stop.domain.auth.dto.result.LoginResult;
import com.sparta.one_stop.domain.auth.service.AuthService;
import com.sparta.one_stop.domain.cart.service.CartMergeService;
import com.sparta.one_stop.domain.cart.support.GuestCartCookieProvider;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import com.sparta.one_stop.global.util.ClientIpExtractor;
import com.sparta.one_stop.global.util.CookieUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AuthControllerCookieTest {

    @Test
    void loginIssuesRootDeviceCookieAndExpiresLegacyPathCookie() {
        AuthService authService = mock(AuthService.class);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        CookieUtil cookieUtil = mock(CookieUtil.class);
        CartMergeService cartMergeService = mock(CartMergeService.class);
        GuestCartCookieProvider guestCartCookieProvider = mock(GuestCartCookieProvider.class);
        ClientIpExtractor clientIpExtractor = mock(ClientIpExtractor.class);
        AuthController controller = new AuthController(
            authService,
            jwtTokenProvider,
            cookieUtil,
            cartMergeService,
            guestCartCookieProvider,
            clientIpExtractor
        );
        LoginRequest request = new LoginRequest("buyer@test.com", "password");
        LoginResponse loginResponse = new LoginResponse(
            "access-token", 900L, 1L, "buyer@test.com", "buyer", "BUYER");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("User-Agent", "test-agent");

        given(clientIpExtractor.extract(servletRequest)).willReturn("127.0.0.1");
        given(authService.login(request, "device-id", "test-agent", "127.0.0.1"))
            .willReturn(new LoginResult(loginResponse, "refresh-token"));
        given(jwtTokenProvider.getRefreshTokenExpirySeconds()).willReturn(604_800L);
        given(cookieUtil.createHttpOnlyCookie(
            "refresh_token", "refresh-token", 604_800L, "/api/auth"))
            .willReturn("refresh_token=refresh-token; Path=/api/auth");
        given(cookieUtil.createHttpOnlyCookie(
            "device_id", "device-id", 604_800L, "/"))
            .willReturn("device_id=device-id; Path=/");
        given(cookieUtil.createExpiredCookie("device_id", "/api/auth"))
            .willReturn("device_id=; Path=/api/auth; Max-Age=0");
        given(cartMergeService.mergeGuestCartToUserCart(1L, null)).willReturn(false);

        var response = controller.login(request, servletRequest, "device-id", null);

        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
            .containsExactly(
                "refresh_token=refresh-token; Path=/api/auth",
                "device_id=device-id; Path=/",
                "device_id=; Path=/api/auth; Max-Age=0"
            );
    }
}
