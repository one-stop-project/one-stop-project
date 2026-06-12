package com.sparta.one_stop.global.util;

import com.sparta.one_stop.global.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClientIpExtractor {

    // 💡 불필요한 헤더를 제거하고 실무에서 널리 쓰이는 2개의 표준 헤더만 사용
    private static final String[] IP_HEADERS = {
        "X-Forwarded-For",
        "X-Real-IP"
    };

    private final SecurityProperties securityProperties;

    public String extract(HttpServletRequest servletRequest) {
        String remoteAddr = servletRequest.getRemoteAddr();

        // 1. 신뢰할 수 있는 프록시망에서 온 요청이 아니라면? (해커의 IP 스푸핑 공격 방어)
        if (!securityProperties.trustedProxies().contains(remoteAddr)) {
            // 💡 실무 운영 환경의 로그 피로도(Alert Fatigue)를 방지하기 위해 DEBUG 레벨 채택
            log.debug("[SECURITY] 신뢰할 수 없는 프록시/직접 접근({}). XFF 헤더를 무시합니다.", remoteAddr);
            return remoteAddr;
        }

        // 2. 신뢰할 수 있는 프록시망을 거친 경우에만 헤더 파싱 허용
        for (String header : IP_HEADERS) {
            String value = servletRequest.getHeader(header);

            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }

        return remoteAddr;
    }
}

