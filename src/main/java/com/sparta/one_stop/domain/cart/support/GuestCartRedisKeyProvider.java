package com.sparta.one_stop.domain.cart.support;

import org.springframework.stereotype.Component;

/**
 * 비로그인 장바구니 Redis Key Provider
 * - Redis key 생성 규칙을 한 곳에서 관리
 * - GuestCartService와 CartMergeService의 key prefix 중복을 방지
 * - 추후 ZSet 기반 담기 순서 key도 이 클래스에서 관리 예정
 */
@Component
public class GuestCartRedisKeyProvider {

    private static final String GUEST_CART_KEY_PREFIX = "guest:cart:";

    public String buildGuestCartKey(String guestCartId) {
        return GUEST_CART_KEY_PREFIX + guestCartId;
    }

}
