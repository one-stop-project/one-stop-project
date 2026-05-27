package com.sparta.one_stop.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    private static final List<String> ALLOWED_METHODS = List.of(
        "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    );

    // FE -> BE 요청 시 허용할 헤더
    //  - Authorization: JWT 전송 필수
    //  - Content-Type: JSON 요청 필수
    //  - X-Requested-With: AJAX 요청 식별 (선택)
    //  - X-Idempotency-Key: 멱등성 키 (향후 결제 도메인)
    private static final List<String> ALLOWED_HEADERS = List.of(
        "Authorization",
        "Content-Type",
        "X-Requested-With",
        "X-Idempotency-Key"
    );

    // BE -> FE 응답 시 노출할 헤더
    //   - Authorization: JWT 갱신 시 새 토큰 전달 (선택)
    //   - Set-Cookie는 별도 처리 (HttpOnly라 JS 접근 불가)
    private static final List<String> EXPOSED_HEADERS = List.of(
        "Authorization"
    );

    // Preflight 캐싱 시간
    private static final long PREFLIGHT_MAX_AGE = 3600L;

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(allowedOrigins);

        config.setAllowedMethods(ALLOWED_METHODS);

        config.setAllowedHeaders(ALLOWED_HEADERS);

        config.setExposedHeaders(EXPOSED_HEADERS);

        config.setAllowCredentials(true);

        config.setMaxAge(PREFLIGHT_MAX_AGE);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/api/**", config);

        return source;
    }
}
