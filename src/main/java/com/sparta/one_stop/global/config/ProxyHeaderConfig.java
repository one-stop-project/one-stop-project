package com.sparta.one_stop.global.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Nginx/Reverse Proxy 뒤에서 동작할 때 X-Forwarded-* 헤더를 Spring MVC/Security가 인식하게 한다.
 *
 * 필요한 이유:
 * - OAuth2 redirect_uri 생성 시 Spring이 내부 HTTP(8080)가 아니라 외부 HTTPS 도메인을 기준으로 baseUrl을 계산해야 한다.
 * - 예: https://onestop1.duckdns.org/login/oauth2/code/kakao
 *
 * Nginx location / 에 반드시 다음 헤더가 같이 있어야 한다.
 * - X-Forwarded-Proto
 * - X-Forwarded-Host
 * - X-Forwarded-Port
 * - X-Forwarded-For
 * - X-Real-IP
 */
@Configuration
public class ProxyHeaderConfig {

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> bean =
            new FilterRegistrationBean<>(new ForwardedHeaderFilter());

        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
