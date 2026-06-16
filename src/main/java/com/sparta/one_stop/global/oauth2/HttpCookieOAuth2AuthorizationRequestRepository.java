package com.sparta.one_stop.global.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * 쿠키 기반 OAuth2 인가요청 저장소.
 *
 * 세션(STATELESS) 대신 단기 HttpOnly 쿠키에 OAuth2AuthorizationRequest를 담아
 * 인가요청 → Provider → 콜백 사이의 state / redirect_uri / PKCE를 보존한다.
 * 기본 HttpSession 저장소는 STATELESS 정책과 충돌해 콜백에서
 * authorization_request_not_found를 유발하므로 이 구현으로 대체한다.
 *
 * 보안 정책:
 *  - HttpOnly + SameSite=Lax
 *      콜백은 Provider에서 우리 도메인으로 오는 cross-site top-level GET이다.
 *      Strict면 그 이동에서 쿠키가 유실되므로 반드시 Lax.
 *  - 180초 단기 만료 (로그인 왕복에 충분, 그 이상 살아있을 이유 없음)
 *  - 역직렬화 화이트리스트 필터 — 허용 외 클래스는 거부(fail-closed).
 *      쿠키는 클라이언트가 조작 가능하므로 Java 역직렬화 가젯체인을 차단한다.
 *      위조/손상 쿠키는 null 반환 → state 검증 단계에서 자연히 실패 처리.
 */
@Slf4j
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int MAX_AGE_SECONDS = 180;

    // 역직렬화 허용 패키지 화이트리스트. 그 외(!*)는 모두 거부.
    private static final ObjectInputFilter AUTH_REQUEST_FILTER = ObjectInputFilter.Config.createFilter(
        "org.springframework.security.oauth2.**;java.util.**;java.lang.**;!*");

    // 로컬(http)=false, 배포(https)=true. yml: app.oauth2.cookie-secure
    @Value("${app.oauth2.cookie-secure:false}")
    private boolean cookieSecure;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return readCookie(request)
            .map(this::deserialize)
            .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        // null로 들어오면 저장소를 비운다 (Spring Security 계약)
        if (authorizationRequest == null) {
            deleteCookie(response);
            return;
        }
        String value = serialize(authorizationRequest);
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(value, MAX_AGE_SECONDS).toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest loaded = loadAuthorizationRequest(request);
        deleteCookie(response);
        return loaded;
    }

    // ────────────────────── 내부 헬퍼 ──────────────────────

    private Optional<String> readCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
            .filter(c -> COOKIE_NAME.equals(c.getName()))
            .map(Cookie::getValue)
            .filter(v -> v != null && !v.isBlank())
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

    private String serialize(OAuth2AuthorizationRequest authRequest) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(authRequest);
            return Base64.getUrlEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            // 직렬화 자체 실패는 우리 측 버그 — 숨기지 않고 드러낸다
            throw new IllegalStateException("OAuth2 인가요청 직렬화 실패", e);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String encoded) {
        final byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            return null; // 손상된 쿠키
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            ois.setObjectInputFilter(AUTH_REQUEST_FILTER);
            return (OAuth2AuthorizationRequest) ois.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            // 위조 / 손상 / 허용외 클래스 → 무시. state 검증에서 실패로 귀결됨
            log.debug("[OAuth2] 인가요청 쿠키 역직렬화 실패 — 무시 처리");
            return null;
        }
    }
}
