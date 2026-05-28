package com.sparta.one_stop.global.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
public class SecurityHeaderConfig {


    // HSTS 활성화 여부
    // - local : false (HTTP 사용)
    // - prod : true (HTTPS 강제)
    @Value("${app.security.hsts.enabled:false}")
    private boolean hstsEnabled;

    // CSP 정책
    @Value("${app.security.csp.policy:default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:;}")
    private String cspPolicy;

    // HttpSecurity에 적용할 헤더 커스터마이저
    @Bean
    public Customizer<HeadersConfigurer<HttpSecurity>> headers() {
        return headers -> {

            // 1. X-Frame-Options : DENY
            // 방어 : 클락재킹
            // 옵션 :
            //   DENY:   모든 iframe 차단(최강)
            //   SAMEORIGIN: 같은 도메인 iframe만 허용

            // 선택 : DENY
            // 이유 : 이커머스는 외부 임베드 필요 없음

            headers.frameOptions(frame -> frame.deny());


            // 2. X-Content-Type-Options
            // 방어 : 브라우저의 MIME 스니핑
            // 공격 시나리오 :
            //   1. 공격자가 .jpg 확장자로 JS 파일 업로드
            //   2. 브라우저가 Content-Type 무시하고 JS로 실행
            //   3. XSS 발생
            // Spring 기본값으로 이미 활성화되지만 명시
            headers.contentTypeOptions(Customizer.withDefaults());

            // 3. Content-Security-Policy
            // 방어 : XSS 최후 보루
            //
            // 동작 :
            //    - 허영된 출처에서만 리소스 로드
            //    - 인라인 스크립트 차단(기본)
            //    - eval() 차단 (기본)
            //
            //  주의 :
            //    - 가장 잘 깨지는 헤더
            //    - 적용 후 브라우저 콘솔 위반 확인 필수
            //    - 환경별로 분리 권장
            headers.contentSecurityPolicy(csp -> csp
                .policyDirectives(cspPolicy));

            // 4. Strict-Transport-Security (HSTS)
            // 방어 : HTTPS 다운그레이드 공격
            //
            // 공격 시나리오 :
            //   1. 사용자가 http://url 입력
            //   2. 중간자 공격으로 HTTPS 리다이렉트 가로챔
            //   3. HTTP 통신으로 토큰 등 탈취
            //
            // HSTS 동작 :
            //   - "이 도메인은 1년간 HTTPS만 써라" 브라우저에 명령
            //   - 다음 방문부터 자동 HTTPS 요청
            //
            // 주의사항 :
            //   - 한 번 적용되면 1년간 HTTP 사용 불가(브라우저 캐시)
            //   - 운영 HTTPS 확실히 적용 후 켤 것
            //   - includeSubDomains는 신중히 (서브도메인 HTTP 있으면 안 됨)
            if (hstsEnabled) {
                headers.httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000L)
                    .preload(false));
            }

            // 5. Referrer-Policy
            // 방어 : 외부 사이트로 이동 시 우리 URL 정보 노출 제한
            //
            // 옵션 :
            //   no-referrer: Referer 헤더 안 보냄
            //   no-referrer-when-downgrade: HTTPS -> HTTP만 차단(가본)
            //   same-origin : 같은 도메인만
            //   strict-origin : HTTPS 유지 시 도메인만
            //   strict-origin-when-cross-origin : 같은 도메인은 풀 URL, 크로스는 도메인만
            //
            //  선택사항 : strict-origin-when-cross-origin
            //  이유:
            //    - 같은 도메인 내 분석은 가능(전체 URL)
            //    - 외부 사이트엔 도메인만 노출(URL의 쿼리 파라미터 보호)
            headers.referrerPolicy(referrer -> referrer
                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
            );

            // 6. Permissions-Policy (구 Feature-Policy)
            // 방어: 브라우저 권한(카메라/마이크/위치 등) 사용 제한
            //
            // XSS가 발생해도:
            //   - 사용자 위치 추적 불가
            //   - 카메라/마이크 접근 불가
            //
            // 우리 정책 :
            //   - 이커머스는 위치 정보 안 씀
            //   - 카메라/마이크 안 씀
            //   - 향후 필요 시 항목별 활성화
            //
            // Spring Security 6에 builder 메서드 활용
            headers.permissionsPolicyHeader(permissions -> permissions
                .policy("geolocation=(), microphone=(), camera=(), payment=(), usb=()")
            );

            // 7. Cache-Control
            // 방어 : 인증 응답이 브라우저/프록시에 캐싱되는 것
            //
            // 공격 시나리오 :
            //   1. 사용자 A가 PC방 로그인
            //   2. 응답에 토큰 등 민감 정보 포함
            //   3. 브라우저 캐시에 저장됨
            //   4. 다음 사용자 B가 같은 PC 사용 -> 캐시에서 토큰 노출
            //
            // Spring Security 기본값 :
            //   Cache-Control: no-cache, no-store, max-age=0, must-revalidate
            //   Pragma: no-cache
            //   Expires: 0
            //
            //  -> 별도 설정 안 해도 적용됨(참고용 주석만)
            //  -> 만약 변경하려면:
            //      headers.cacheControl(cache -> cache.disable());

            // Cross-Origin 정책(Spring Security 6.x 신규)
            //   1. Cross-Origin-Opener-Policy(COOP)
            //   2. Cross-Origin-Embedder-Policy(COEP)
            //   3. Cross-Origin-Resource-Policy(CORP)
            //
            //  Spectre 같은 사이드 채널 공격 방어
            //  현재는 옵션 - 향후 필요 시 활성화
            headers.crossOriginOpenerPolicy(coop -> coop
                .policy(CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN)
                );
        };
    }
}
