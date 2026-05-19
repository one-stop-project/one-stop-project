package com.sparta.one_stop.global.config;

import com.sparta.one_stop.global.security.JwtAccessDeniedHandler;
import com.sparta.one_stop.global.security.JwtAuthenticationEntryPoint;
import com.sparta.one_stop.global.security.JwtAuthenticationFilter;
import com.sparta.one_stop.global.security.JwtExceptionFilter;
import com.sparta.one_stop.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtExceptionFilter jwtExceptionFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;



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

            // 기본 로그인 비활성화
            .formLogin(AbstractHttpConfigurer::disable)

            // HttpBasic 비활성화 only JWT
            .httpBasic(AbstractHttpConfigurer::disable)

            // Session 비활성화 (JWT Stateless 방식)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            //
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler))

            // URL별 접근 권한 설정
            .authorizeHttpRequests(auth -> auth

                // logout 별도 구성 / 인증 반드시 필요
                .requestMatchers(HttpMethod.POST,"/api/auth/logout").authenticated()

                // 인증 없이 접근 가능
                // AUTH부분은 정책 변경 소요 대비 분리 작성
                .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                .requestMatchers("/api/cart/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/subscriptions/plans").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                // 구매자만 접근가능
                .requestMatchers("/api/orders/**").hasRole("BUYER")
                .requestMatchers("/api/reviews/**").hasRole("BUYER")
                .requestMatchers("/api/subscriptions/**").hasRole("BUYER")
                .requestMatchers("/api/coupons/**").hasRole("BUYER")

                // 관리자만 접근 가능
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                // 판매자만 접근 가능
                .requestMatchers("/api/seller/**").hasRole("SELLER")

                // 나머지는 로그인 필요
                .anyRequest().authenticated()
            )

            // ── 필터 체인 등록 ── 미인증, 권한처리
            // 순서: JwtExceptionFilter → JwtAuthenticationFilter → UPAF
            // JwtExceptionFilter가 JwtAuthenticationFilter의 예외를 catch
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtExceptionFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
        AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
