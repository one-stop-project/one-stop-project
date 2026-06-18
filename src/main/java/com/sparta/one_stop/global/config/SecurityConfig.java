package com.sparta.one_stop.global.config;

import com.sparta.one_stop.global.oauth2.CustomOAuth2UserService;
import com.sparta.one_stop.global.oauth2.HttpCookieOAuth2AuthorizationRequestRepository;
import com.sparta.one_stop.global.oauth2.OAuth2FailureHandler;
import com.sparta.one_stop.global.oauth2.OAuth2SuccessHandler;
import com.sparta.one_stop.global.security.JwtAccessDeniedHandler;
import com.sparta.one_stop.global.security.JwtAuthenticationEntryPoint;
import com.sparta.one_stop.global.security.JwtAuthenticationFilter;
import com.sparta.one_stop.global.security.JwtExceptionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
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

    // ── OAuth2 (소셜 로그인) ──
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieAuthRequestRepository;


    @Bean
    @Order(0)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

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
            //   ※ oauth2Login의 인가요청 state는 세션 대신 쿠키 저장소로 보존한다.
            //     (아래 authorizationRequestRepository 참고 — STATELESS와의 충돌 해소)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 인증/권한 실패 처리
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler))

            // URL별 접근 권한 설정
            .authorizeHttpRequests(auth -> auth

                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 정적 리소스 허용 (JS, CSS, 이미지, favicon 등)[FE]
                //    Spring Boot 기본 정적 위치(/static, /public 등) 자동 허용
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()

                // SPA 진입점 + 주요 페이지 경로 허용[FE]
                //    React 화면이 뜨려면 index.html과 라우트 경로가 인증 없이 열려야 함
                //    (실제 데이터는 각 /api/** 호출에서 인증 처리됨)
                .requestMatchers(
                    "/",                    // 루트 (index.html)
                    "/index.html",
                    "/assets/**",           // Vite 빌드 산출물 (JS/CSS 청크)
                    "/favicon.ico",
                    "/vite.svg"
                ).permitAll()

                // SPA 라우트 경로 허용 (점이 없는 경로 = 화면 라우트)[FE]
                //    /products, /cart, /login 등 React Router 경로
                //    이 경로들은 SpaForwardController가 index.html로 forward
                .requestMatchers(
                    "/login", "/signup",
                    "/products/**", "/cart", "/checkout",
                    "/orders/**", "/payment/**",
                    "/mypage/**", "/seller/**", "/admin/**"
                ).permitAll()

                // OAuth2(소셜 로그인) 진입/콜백 경로 허용
                //   /oauth2/authorization/{provider}  : 인가요청 시작
                //   /login/oauth2/code/{provider}      : Provider 콜백 (state 검증 → 토큰 발급)
                //   ※ /login은 위에서 '정확 매칭' permitAll이라 콜백 하위경로를 덮지 못하므로 별도 명시
                .requestMatchers("/oauth2/**", "/login/oauth2/code/**").permitAll()

                // logout — permitAll (만료된 AT로도 로그아웃 가능해야 함)
                //   인증 필터에서 막으면 AT 만료 시 RT/기기 정리 경로가 영원히 차단됨(데드락)
                //   컨트롤러가 getUserIdAllowExpired로 만료 토큰도 파싱해 best-effort 정리
                .requestMatchers(HttpMethod.POST,"/api/auth/logout").permitAll()

                // 인증 없이 접근 가능
                // AUTH부분은 정책 변경 소요 대비 분리 작성
                .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/oauth2/exchange").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                .requestMatchers("/api/carts/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/subscriptions/plans").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // 구매자만 접근가능
                .requestMatchers("/api/orders/**").hasRole("BUYER")
                .requestMatchers("/api/reviews/**").hasRole("BUYER")
                .requestMatchers("/api/subscriptions/**").hasRole("BUYER")
                .requestMatchers("/api/coupons/**").hasRole("BUYER")
                .requestMatchers("/api/notifications/**").hasRole("BUYER")
                .requestMatchers(HttpMethod.GET, "/api/users/me/coupons", "/api/users/me/coupons/**").hasRole("BUYER")
                .requestMatchers(HttpMethod.POST, "/api/users/me/points/charge").hasAnyRole("ADMIN", "SUPER_ADMIN") // 테스트 충전 방어
                .requestMatchers("/api/users/me/points", "/api/users/me/points/**").hasRole("BUYER")

                // User 공통(BUYER + SELLER 모두 접근 가능)
                // 마이페이지는 모두 조회
                .requestMatchers("/api/users/me/**").authenticated()

                // 관리자만 접근 가능
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                // 판매자만 접근 가능
                .requestMatchers("/api/seller/**").hasRole("SELLER")

                // 나머지는 로그인 필요
                .anyRequest().authenticated()
            )

            // ── OAuth2 로그인 ──
            //   흐름: 진입(/oauth2/authorization/kakao) → Provider 동의 → 콜백(/login/oauth2/code/kakao)
            //         → CustomOAuth2UserService.loadUser(매칭/가입) → SuccessHandler(JWT 발급)
            //   인가요청 저장소: 세션 대신 쿠키(STATELESS 충돌 해소, SameSite=Lax)
            .oauth2Login(oauth -> oauth
                .authorizationEndpoint(ae -> ae
                    .authorizationRequestRepository(cookieAuthRequestRepository))
                .userInfoEndpoint(ui -> ui
                    .userService(customOAuth2UserService))
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler))

            // ── 필터 체인 등록 ── 미인증, 권한처리
            // 순서: JwtExceptionFilter → JwtAuthenticationFilter → UPAF
            // JwtExceptionFilter가 JwtAuthenticationFilter의 예외를 catch
            //   ※ JwtAuthenticationFilter는 토큰 없으면 통과(lenient)하므로
            //     OAuth 콜백(헤더 없는 리다이렉트)은 걸리지 않는다.
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
