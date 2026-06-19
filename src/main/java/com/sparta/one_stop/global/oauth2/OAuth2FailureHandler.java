package com.sparta.one_stop.global.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * OAuth2 로그인 실패 핸들러.
 *
 * 실패 시 프론트 에러 라우트로 리다이렉트한다. 내부 예외 메시지는 노출하지 않고,
 * 약속된 에러코드만 쿼리 파라미터로 전달한다.
 *   - AUTH_019 : 이메일 충돌(이미 일반 회원으로 가입된 이메일)
 *   - AUTH_018 : 이메일 미수신 / 처리 중 오류 등
 *   - OAUTH2_FAILED : 그 외 일반 실패
 *
 * 잔여 인가요청 쿠키도 best-effort로 정리한다(대부분 인증 필터가 이미 제거).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final HttpCookieOAuth2AuthorizationRequestRepository authRequestRepository;

    /**
     * OAuth2 실패 후 최종 redirect URI.
     * 서버 배포 환경에서는 localhost:3001을 사용하지 않는다.
     */
    @Value("${app.oauth2.failure-redirect-uri:https://onestop1.duckdns.org/login}")
    private String failureRedirectUri;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        // 잔여 쿠키 정리 (방어적)
        authRequestRepository.removeAuthorizationRequest(request, response);

        String errorCode = resolveErrorCode(exception);
        log.warn("[OAuth2] 로그인 실패: code={}", errorCode);

        String target = UriComponentsBuilder.fromUriString(failureRedirectUri)
            .queryParam("error", errorCode)
            .build()
            .toUriString();

        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private String resolveErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oae) {
            String code = oae.getError().getErrorCode();
            return (code != null && !code.isBlank()) ? code : "OAUTH2_FAILED";
        }
        return "OAUTH2_FAILED";
    }
}
