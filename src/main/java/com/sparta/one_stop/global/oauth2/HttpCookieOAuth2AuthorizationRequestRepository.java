package com.sparta.one_stop.global.oauth2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Stateless OAuth2 authorization request repository.
 * The minimal request DTO is stored as signed JSON to avoid native Java deserialization.
 */
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int MAX_AGE_SECONDS = 180;
    private static final int MAX_COOKIE_VALUE_LENGTH = 12_000;
    private static final int MIN_SIGNING_KEY_BYTES = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final byte[] signingKey;

    @Value("${app.oauth2.cookie-secure:false}")
    private boolean cookieSecure;

    public HttpCookieOAuth2AuthorizationRequestRepository(
        ObjectMapper objectMapper,
        @Value("${app.oauth2.authorization-request-cookie-secret:${jwt.secret.key}}") String signingSecret
    ) {
        this.objectMapper = objectMapper;
        this.signingKey = decodeSecret(signingSecret);
        if (signingKey.length < MIN_SIGNING_KEY_BYTES) {
            throw new IllegalStateException("OAuth2 authorization request cookie secret must be at least 32 bytes");
        }
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request).map(this::deserialize).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(response);
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE,
            buildCookie(serialize(authorizationRequest), MAX_AGE_SECONDS).toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest loaded = loadAuthorizationRequest(request);
        deleteCookie(response);
        return loaded;
    }

    private Optional<String> readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
            .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value != null && !value.isBlank())
            .findFirst();
    }

    private ResponseCookie buildCookie(String value, int maxAgeSeconds) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .path("/")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Lax")
            .maxAge(maxAgeSeconds)
            .build();
    }

    private void deleteCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
    }

    private String serialize(OAuth2AuthorizationRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(AuthorizationRequestCookie.from(request));
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(json);
            return payload + "." + sign(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("OAuth2 authorization request JSON serialization failed", e);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String encoded) {
        if (encoded.length() > MAX_COOKIE_VALUE_LENGTH) return null;
        int separator = encoded.lastIndexOf('.');
        if (separator <= 0 || separator == encoded.length() - 1) return null;

        String payload = encoded.substring(0, separator);
        String suppliedSignature = encoded.substring(separator + 1);
        if (!isValidSignature(payload, suppliedSignature)) return null;

        try {
            byte[] json = Base64.getUrlDecoder().decode(payload);
            return objectMapper.readValue(json, AuthorizationRequestCookie.class)
                .toAuthorizationRequest();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("OAuth2 authorization request cookie signing failed", e);
        }
    }

    private boolean isValidSignature(String payload, String suppliedSignature) {
        byte[] expected = sign(payload).getBytes(StandardCharsets.US_ASCII);
        byte[] supplied = suppliedSignature.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, supplied);
    }

    private static byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("OAuth2 authorization request cookie secret is required");
        }
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ignored) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    private record AuthorizationRequestCookie(
        String authorizationUri,
        String clientId,
        String redirectUri,
        Set<String> scopes,
        String state,
        Map<String, Object> additionalParameters,
        String authorizationRequestUri,
        Map<String, Object> attributes
    ) {
        private static AuthorizationRequestCookie from(OAuth2AuthorizationRequest request) {
            return new AuthorizationRequestCookie(
                request.getAuthorizationUri(), request.getClientId(), request.getRedirectUri(),
                request.getScopes(), request.getState(), request.getAdditionalParameters(),
                request.getAuthorizationRequestUri(), request.getAttributes());
        }

        private OAuth2AuthorizationRequest toAuthorizationRequest() {
            return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(authorizationUri)
                .clientId(clientId)
                .redirectUri(redirectUri)
                .scopes(scopes == null ? Set.of() : scopes)
                .state(state)
                .additionalParameters(additionalParameters == null ? Map.of() : additionalParameters)
                .authorizationRequestUri(authorizationRequestUri)
                .attributes(attributes == null ? Map.of() : attributes)
                .build();
        }
    }
}
