package com.sparta.one_stop.domain.cart.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuestCartRedisKeyProviderTest {

    private final GuestCartRedisKeyProvider guestCartRedisKeyProvider =
        new GuestCartRedisKeyProvider();

    @Test
    @DisplayName("비로그인 장바구니 Hash key 생성 성공")
    void buildGuestCartKey_success() {
        // given
        String guestCartId = "test-guest-cart-id";

        // when
        String redisKey = guestCartRedisKeyProvider.buildGuestCartKey(
            guestCartId
        );

        // then
        assertThat(redisKey).isEqualTo(
            "guest:cart:test-guest-cart-id"
        );
    }

    @Test
    @DisplayName("비로그인 장바구니 ZSet order key 생성 성공")
    void buildGuestCartOrderKey_success() {
        // given
        String guestCartId = "test-guest-cart-id";

        // when
        String orderKey = guestCartRedisKeyProvider.buildGuestCartOrderKey(
            guestCartId
        );

        // then
        assertThat(orderKey).isEqualTo(
            "guest:cart-order:test-guest-cart-id"
        );
    }

}
