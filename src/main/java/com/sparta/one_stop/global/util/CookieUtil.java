package com.sparta.one_stop.global.util;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    public String createHttpOnlyCookie(String name, String value, long maxAgeSeconds, String path) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true) // HTTPS 환경에서만 전송 (로컬 테스트 시 HTTP라면 false로 임시 변경 필요)
            .path(path)
            .maxAge(maxAgeSeconds)
            .sameSite("Strict") // CSRF 방어
            .build()
            .toString();
    }

    public String createExpiredCookie(String name, String path) {
        return ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(true)
            .path(path)
            .maxAge(0) // 즉시 만료
            .build()
            .toString();
    }
}
