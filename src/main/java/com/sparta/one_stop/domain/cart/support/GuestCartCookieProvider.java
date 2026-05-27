package com.sparta.one_stop.domain.cart.support;

import com.sparta.one_stop.global.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 비로그인 장바구니 식별 쿠키 Provider
 * - guest_cart_id 쿠키 발급 / 갱신 / 삭제 담당
 * - 실제 쿠키 문자열 생성은 공통 CookieUtil에 위임
 * - guest_cart_id는 Redis 비로그인 장바구니 key 생성에 사용됨
 */
@Component
@RequiredArgsConstructor
public class GuestCartCookieProvider {

    // 비로그인 장바구니 식별용 쿠키 이름
    public static final String GUEST_CART_COOKIE_NAME = "guest_cart_id";

    // 전체 API 경로에서 guest_cart_id 쿠키를 사용할 수 있도록 루트 경로로 설정
    private static final String COOKIE_PATH = "/";

    // Redis 장바구니 TTL과 동일하게 쿠키 만료 시간도 7일로 설정
    private static final long MAX_AGE_SECONDS = 7 * 24 * 60 * 60;

    // 로컬 HTTP 테스트를 위해 false 설정
    // TODO: 운영 환경에서는 true로 변경하거나 application.yml 설정값으로 분리
    private static final boolean COOKIE_SECURE = false;

    // 비로그인 장바구니 식별 쿠키는 일반 페이지 이동/요청에서 유지되도록 Lax 사용
    private static final String COOKIE_SAME_SITE = "Lax";

    private final CookieUtil cookieUtil;

    /**
     * guest_cart_id 쿠키 조회 또는 신규 발급
     * - 기존 쿠키가 있으면 만료 시간을 7일로 갱신
     * - 기존 쿠키가 없으면 UUID를 생성하여 새 쿠키 발급
     */
    public String resolveOrCreate(
        String guestCartId,
        HttpServletResponse response
    ) {
        if (guestCartId != null && !guestCartId.isBlank()) {
            refreshCookie(
                guestCartId,
                response
            );
            return guestCartId;
        }

        String newGuestCartId = UUID.randomUUID().toString();

        refreshCookie(
            newGuestCartId,
            response
        );

        return newGuestCartId;
    }

    /**
     * guest_cart_id 쿠키 발급/갱신
     * - 장바구니 조회 / 담기 / 수량 변경 / 삭제 시 호출
     * - Redis TTL 갱신 정책과 쿠키 만료 시간을 맞추기 위해 사용
     */
    public void refreshCookie(
        String guestCartId,
        HttpServletResponse response
    ) {
        String cookie = cookieUtil.createHttpOnlyCookie(
            GUEST_CART_COOKIE_NAME,
            guestCartId,
            MAX_AGE_SECONDS,
            COOKIE_PATH,
            COOKIE_SECURE,
            COOKIE_SAME_SITE
        );

        response.addHeader(
            HttpHeaders.SET_COOKIE,
            cookie
        );
    }

    /**
     * guest_cart_id 쿠키 삭제
     * - HttpServletResponse에 직접 만료 쿠키를 추가해야 하는 경우 사용
     * - 로그인 후 Redis 장바구니 merge가 정상 처리된 경우 guest_cart_id 쿠키 삭제에 사용
     */
    public void deleteCookie(HttpServletResponse response) {
        String cookie = cookieUtil.createExpiredCookie(
            GUEST_CART_COOKIE_NAME,
            COOKIE_PATH,
            COOKIE_SECURE,
            COOKIE_SAME_SITE
        );

        response.addHeader(
            HttpHeaders.SET_COOKIE,
            cookie
        );
    }

    /**
     * guest_cart_id 만료 쿠키 문자열 생성
     * - ResponseEntity header 방식으로 쿠키를 내려줘야 하는 컨트롤러에서 사용
     * - 로그인 후 Redis 장바구니 merge가 끝난 뒤 guest_cart_id 쿠키 삭제에 사용
     */
    public String createExpiredCookie() {
        return cookieUtil.createExpiredCookie(
            GUEST_CART_COOKIE_NAME,
            COOKIE_PATH,
            COOKIE_SECURE,
            COOKIE_SAME_SITE
        );
    }

}
