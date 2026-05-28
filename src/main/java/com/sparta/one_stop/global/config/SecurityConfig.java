package com.sparta.one_stop.global.config;

import com.sparta.one_stop.global.security.JwtAccessDeniedHandler;
import com.sparta.one_stop.global.security.JwtAuthenticationEntryPoint;
import com.sparta.one_stop.global.security.JwtAuthenticationFilter;
import com.sparta.one_stop.global.security.JwtExceptionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// 필터 체인 순서
// 1. CORS -> Preflight OPTIONS 먼저 통과시켜야 함
// 2. 헤더 -> 모든 응답에 보안 헤더 적용(CORS 응답)
// 3. CRSF -> REST API는 비활성화
// 4. 인증 -> 토큰 검증
// 5. 권한 -> URL별 접근 제어

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtExceptionFilter jwtExceptionFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final UrlBasedCorsConfigurationSource corsConfigurationSource;
    private final SecurityHeaderConfig securityHeadersConfig;


    // @Component 필터의 서블릿 자동 등록 비활성화 (Security 체인에서만 실행되도록)
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<JwtExceptionFilter> jwtExceptionFilterRegistration(JwtExceptionFilter filter) {
        FilterRegistrationBean<JwtExceptionFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt: 단방향 해시, 자동 솔트 생성으로 레인보우 테이블 공격 방지
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CORS 활성화(필터 체인의 가장 앞에서 처리되어야 함. * 수정시 참고바람)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            // 보안 헤더 적용
            .headers(securityHeadersConfig.headers())

            // CSRF 비활성화 (REST API + JWT 사용)
            .csrf(AbstractHttpConfigurer::disable)

            // 기본 로그인 비활성화
            .formLogin(AbstractHttpConfigurer::disable)

            // HttpBasic 비활성화 only JWT
            .httpBasic(AbstractHttpConfigurer::disable)

            // Session 비활성화 (JWT Stateless 방식)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 인증/권한 실패 처리
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler))

            // URL별 접근 권한 설정
            .authorizeHttpRequests(auth -> auth

                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // logout 별도 구성 / 인증 반드시 필요
                .requestMatchers(HttpMethod.POST,"/api/auth/logout").authenticated()

                // 인증 없이 접근 가능
                // AUTH부분은 정책 변경 소요 대비 분리 작성
                .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                .requestMatchers("/api/carts/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/subscriptions/plans").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                // User 공통(BUYER + SELLER 모두 접근 가능)
                // 마이페이지는 모두 조회
                .requestMatchers("/api/users/me/**").authenticated()

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

