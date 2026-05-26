package com.sparta.one_stop.global.util;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 공통 쿠키 생성 유틸
 * - ResponseCookie 생성 로직을 한 곳에서 관리
 * - 도메인별 쿠키 정책은 필요한 경우 오버로딩 메서드로 secure/sameSite 값을 지정해서 사용
 */
@Component
public class CookieUtil {

    /**
     * 기본 HttpOnly 쿠키 생성
     * - 기존 인증/공통 쿠키 정책 유지용
     * - secure=true: HTTPS 환경에서만 쿠키 전송
     * - SameSite=Strict: 크로스 사이트 요청에서 쿠키 전송 제한
     * 로컬 HTTP 환경에서는 secure=true 쿠키가 저장/전송되지 않을 수 있으므로,
     * 로컬 테스트 또는 도메인별 정책이 필요한 경우 오버로딩 메서드 사용
     */
    public String createHttpOnlyCookie(
        String name,
        String value,
        long maxAgeSeconds,
        String path
    ) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true)
            .path(path)
            .maxAge(maxAgeSeconds)
            .sameSite("Strict")
            .build()
            .toString();
    }

    /**
     * 기본 쿠키 만료 처리
     * - 기존 인증/공통 쿠키 삭제 정책 유지용
     * - maxAge=0으로 즉시 만료
     * secure/sameSite를 도메인별로 맞춰야 하는 경우 오버로딩 메서드 사용
     */
    public String createExpiredCookie(
        String name,
        String path
    ) {
        return ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(true)
            .path(path)
            .maxAge(0)
            .build()
            .toString();
    }

    /**
     * 도메인별 정책을 적용한 HttpOnly 쿠키 생성
     * - 비로그인 장바구니처럼 secure/sameSite 설정을 별도로 제어해야 하는 경우 사용
     * - 예: 로컬 개발 환경 secure=false, SameSite=Lax
     */
    public String createHttpOnlyCookie(
        String name,
        String value,
        long maxAgeSeconds,
        String path,
        boolean secure,
        String sameSite
    ) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .path(path)
            .maxAge(maxAgeSeconds)
            .sameSite(sameSite)
            .build()
            .toString();
    }

    /**
     * 도메인별 정책을 적용한 쿠키 만료 처리
     * - 쿠키 삭제 시에도 생성 시 사용한 secure/sameSite 정책과 맞춰 사용
     * - guest_cart_id 등 도메인 전용 쿠키 삭제에 사용
     */
    public String createExpiredCookie(
        String name,
        String path,
        boolean secure,
        String sameSite
    ) {
        return ResponseCookie.from(name, "")
            .httpOnly(true)
            .secure(secure)
            .path(path)
            .maxAge(0)
            .sameSite(sameSite)
            .build()
            .toString();
    }

}
