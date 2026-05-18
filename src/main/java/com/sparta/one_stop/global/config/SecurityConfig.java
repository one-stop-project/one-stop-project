package com.sparta.one_stop.global.config;

import com.sparta.one_stop.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정
 * - JWT 기반 Stateless 인증
 * - BUYER / SELLER / ADMIN 3-Role 접근 제어
 * - CSRF 비활성화 (REST API이므로)
 * - Session 비활성화 (JWT 사용이므로)
 *
 */

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt: 단방향 해시, 자동 솔트 생성으로 레인보우 테이블 공격 방지
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF 비활성화 (REST API + JWT 사용)
            .csrf(AbstractHttpConfigurer::disable)

            // Session 비활성화 (JWT Stateless 방식)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // URL별 접근 권한 설정
            .authorizeHttpRequests(auth -> auth

                // 인증 없이 접근 가능
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                // 관리자만 접근 가능
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                // 판매자만 접근 가능
                .requestMatchers("/api/seller/**").hasRole("SELLER")

                // 나머지는 로그인 필요
                .anyRequest().authenticated()
            )

            // 미인증 요청 처리 (401)
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"success\":false,\"status\":401,\"code\":\"AUTH_007\",\"message\":\"로그인이 필요합니다\"}"
                    );
                })
                // 권한 없음 처리 (403)
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                        "{\"success\":false,\"status\":403,\"code\":\"AUTH_011\",\"message\":\"접근 권한이 없습니다\"}"
                    );
                })
            );

        return http.build();
    }
}
