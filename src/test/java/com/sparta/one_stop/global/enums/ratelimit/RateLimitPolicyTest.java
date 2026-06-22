package com.sparta.one_stop.global.enums.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPolicyTest {

    @Test
    void production_login_limits_are_not_load_test_overrides() {
        assertThat(RateLimitPolicy.LOGIN_PER_ACCOUNT.getLimit()).isEqualTo(5);
        assertThat(RateLimitPolicy.LOGIN_CONCURRENT_PER_ACCOUNT.getLimit()).isEqualTo(10);
        assertThat(RateLimitPolicy.DEVICE_REGISTER_PER_ACCOUNT.getLimit()).isEqualTo(3);
        assertThat(RateLimitPolicy.DEVICE_REGISTER_PER_IP.getLimit()).isEqualTo(10);
        assertThat(RateLimitPolicy.LOGIN_PER_IP.getLimit()).isEqualTo(20);
        assertThat(RateLimitPolicy.LOGIN_PER_GLOBAL.getLimit()).isEqualTo(1_000);
    }
}
