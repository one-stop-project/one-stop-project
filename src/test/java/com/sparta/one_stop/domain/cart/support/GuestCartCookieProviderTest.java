package com.sparta.one_stop.domain.cart.support;

import com.sparta.one_stop.global.util.CookieUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collection;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class GuestCartCookieProviderTest {

    private final CookieUtil cookieUtil = new CookieUtil();

    private final GuestCartCookieProvider guestCartCookieProvider =
        new GuestCartCookieProvider(cookieUtil);

    @Test
    @DisplayName("resolveOrCreate 성공 - 기존 guestCartId가 있으면 해당 값을 반환하고 쿠키를 갱신한다")
    void resolveOrCreate_success_whenGuestCartIdExists() {
        // given
        String guestCartId = "existing-guest-cart-id";
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String result = guestCartCookieProvider.resolveOrCreate(
            guestCartId,
            response
        );

        // then
        assertThat(result).isEqualTo(guestCartId);

        String cookie = getOnlySetCookie(response);

        assertRefreshCookie(cookie, guestCartId);
    }

    @Test
    @DisplayName("resolveOrCreate 성공 - guestCartId가 없으면 UUID를 생성하고 쿠키를 발급한다")
    void resolveOrCreate_success_whenGuestCartIdDoesNotExist() {
        // given
        String guestCartId = null;
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String result = guestCartCookieProvider.resolveOrCreate(
            guestCartId,
            response
        );

        // then
        assertThat(result).isNotBlank();
        assertThatNoException().isThrownBy(() -> UUID.fromString(result));

        String cookie = getOnlySetCookie(response);

        assertRefreshCookie(cookie, result);
    }

    @Test
    @DisplayName("resolveOrCreate 성공 - guestCartId가 blank이면 UUID를 생성하고 쿠키를 발급한다")
    void resolveOrCreate_success_whenGuestCartIdIsBlank() {
        // given
        String guestCartId = " ";
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        String result = guestCartCookieProvider.resolveOrCreate(
            guestCartId,
            response
        );

        // then
        assertThat(result).isNotBlank();
        assertThat(result).isNotEqualTo(guestCartId);
        assertThatNoException().isThrownBy(() -> UUID.fromString(result));

        String cookie = getOnlySetCookie(response);

        assertRefreshCookie(cookie, result);
    }

    @Test
    @DisplayName("refreshCookie 성공 - guest_cart_id 쿠키를 Set-Cookie 헤더에 추가한다")
    void refreshCookie_success() {
        // given
        String guestCartId = "guest-id";
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        guestCartCookieProvider.refreshCookie(
            guestCartId,
            response
        );

        // then
        String cookie = getOnlySetCookie(response);

        assertRefreshCookie(cookie, guestCartId);
    }

    @Test
    @DisplayName("deleteCookie 성공 - guest_cart_id 만료 쿠키를 Set-Cookie 헤더에 추가한다")
    void deleteCookie_success() {
        // given
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        guestCartCookieProvider.deleteCookie(response);

        // then
        String cookie = getOnlySetCookie(response);

        assertExpiredCookie(cookie);
    }

    @Test
    @DisplayName("createExpiredCookie 성공 - guest_cart_id 만료 쿠키 문자열을 생성한다")
    void createExpiredCookie_success() {
        // when
        String cookie = guestCartCookieProvider.createExpiredCookie();

        // then
        assertExpiredCookie(cookie);
    }

    private void assertRefreshCookie(String cookie, String guestCartId) {
        assertThat(cookie).contains("guest_cart_id=" + guestCartId);
        assertThat(cookie).contains("Path=/");
        assertThat(cookie).contains("Max-Age=604800");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("SameSite=Lax");
    }

    private void assertExpiredCookie(String cookie) {
        assertThat(cookie).contains("guest_cart_id=");
        assertThat(cookie).contains("Path=/");
        assertThat(cookie).contains("Max-Age=0");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("SameSite=Lax");
    }

    private String getOnlySetCookie(MockHttpServletResponse response) {
        Collection<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);

        assertThat(setCookies).hasSize(1);

        return setCookies.iterator().next();
    }

}
