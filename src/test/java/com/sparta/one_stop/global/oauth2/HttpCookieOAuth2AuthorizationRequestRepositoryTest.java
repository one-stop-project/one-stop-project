package com.sparta.one_stop.global.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Map;
import java.util.Set;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    private static final String SIGNING_SECRET = Base64.getEncoder().encodeToString(
        "0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void signedJsonCookieRoundTripsAuthorizationRequest() {
        var repository = new HttpCookieOAuth2AuthorizationRequestRepository(
            new ObjectMapper(), SIGNING_SECRET);
        var authorizationRequest = request();
        var response = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(
            authorizationRequest, new MockHttpServletRequest(), response);

        var callback = new MockHttpServletRequest();
        callback.setCookies(new Cookie(
            HttpCookieOAuth2AuthorizationRequestRepository.COOKIE_NAME,
            cookieValue(response.getHeader("Set-Cookie"))));

        OAuth2AuthorizationRequest restored = repository.loadAuthorizationRequest(callback);
        assertThat(restored.getState()).isEqualTo("state-123");
        assertThat(restored.getClientId()).isEqualTo("client-id");
        assertThat(restored.getAdditionalParameters()).containsEntry("code_challenge", "challenge");
        assertThat(restored.getAttributes()).containsEntry("registration_id", "kakao");
    }

    @Test
    void tamperedCookieIsRejected() {
        var repository = new HttpCookieOAuth2AuthorizationRequestRepository(
            new ObjectMapper(), SIGNING_SECRET);
        var response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(request(), new MockHttpServletRequest(), response);
        String value = cookieValue(response.getHeader("Set-Cookie"));

        var callback = new MockHttpServletRequest();
        callback.setCookies(new Cookie(
            HttpCookieOAuth2AuthorizationRequestRepository.COOKIE_NAME,
            value.substring(0, value.length() - 1) + (value.endsWith("A") ? "B" : "A")));

        assertThat(repository.loadAuthorizationRequest(callback)).isNull();
    }

    private OAuth2AuthorizationRequest request() {
        return OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://kauth.kakao.com/oauth/authorize")
            .clientId("client-id")
            .redirectUri("https://example.com/login/oauth2/code/kakao")
            .scopes(Set.of("profile_nickname"))
            .state("state-123")
            .additionalParameters(Map.of("code_challenge", "challenge"))
            .attributes(Map.of("registration_id", "kakao", "code_verifier", "verifier"))
            .build();
    }

    private String cookieValue(String setCookie) {
        String prefix = HttpCookieOAuth2AuthorizationRequestRepository.COOKIE_NAME + "=";
        int start = setCookie.indexOf(prefix) + prefix.length();
        return setCookie.substring(start, setCookie.indexOf(';', start));
    }
}
