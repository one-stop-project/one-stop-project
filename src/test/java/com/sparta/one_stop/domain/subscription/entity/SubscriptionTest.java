package com.sparta.one_stop.domain.subscription.entity;

import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionTest {

    private User user() {
        return User.builder()
            .email("test@test.com")
            .password("Password1!")
            .name("tester")
            .phone("010-1234-5678")
            .address("Seoul")
            .role(UserRole.BUYER)
            .build();
    }

    private Subscription activeSubscription(LocalDateTime now) {
        return Subscription.builder()
            .user(user())
            .startAt(now)
            .endAt(now.plusDays(30))
            .nextPaymentDate(now.plusDays(30))
            .build();
    }

    @Test
    @DisplayName("구독 생성 성공 - 상태는 ACTIVE")
    void create_success() {

        // given
        LocalDateTime now = LocalDateTime.now();

        // when
        Subscription subscription = activeSubscription(now);

        // then
        assertThat(subscription.getStatus())
            .isEqualTo(SubscriptionStatus.ACTIVE);

        assertThat(subscription.getEndAt())
            .isEqualTo(now.plusDays(30));

        assertThat(subscription.getCancelReason())
            .isNull();
    }

    @Test
    @DisplayName("구독 해지 성공 - ACTIVE → CANCELLED")
    void cancel_success() {

        // given
        Subscription subscription = activeSubscription(LocalDateTime.now());

        // when
        subscription.cancel("단순 변심");

        // then
        assertThat(subscription.getStatus())
            .isEqualTo(SubscriptionStatus.CANCELLED);

        assertThat(subscription.getCancelReason())
            .isEqualTo("단순 변심");
    }

    @Test
    @DisplayName("구독 해지 실패 - 이미 CANCELLED")
    void cancel_fail_alreadyCancelled() {

        // given
        Subscription subscription = activeSubscription(LocalDateTime.now());
        subscription.cancel("first");

        // when & then
        assertThatThrownBy(() ->
            subscription.cancel("second")
        )
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getErrorCode())
            .isEqualTo(ErrorCode.SUBSCRIPTION_003);
    }

    @Test
    @DisplayName("구독 해지 실패 - EXPIRED")
    void cancel_fail_expired() {

        // given
        Subscription subscription = activeSubscription(LocalDateTime.now());
        subscription.expire();

        // when & then
        assertThatThrownBy(() ->
            subscription.cancel("x")
        )
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getErrorCode())
            .isEqualTo(ErrorCode.SUBSCRIPTION_011);
    }

    @Test
    @DisplayName("구독 갱신 성공 - endAt/nextPaymentDate를 현재 기준 30일로 설정")
    void renew_success() {

        // given
        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = activeSubscription(now);

        // when
        subscription.renew(now);

        // then
        assertThat(subscription.getEndAt())
            .isEqualTo(now.plusDays(30));

        assertThat(subscription.getNextPaymentDate())
            .isEqualTo(now.plusDays(30));

        assertThat(subscription.getStatus())
            .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("구독 갱신 실패 - CANCELLED")
    void renew_fail_cancelled() {

        // given
        Subscription subscription = activeSubscription(LocalDateTime.now());
        subscription.cancel("x");

        // when & then
        assertThatThrownBy(() -> subscription.renew(LocalDateTime.now()))
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getErrorCode())
            .isEqualTo(ErrorCode.SUBSCRIPTION_004);
    }

    @Test
    @DisplayName("구독 갱신 실패 - EXPIRED")
    void renew_fail_expired() {

        // given
        Subscription subscription = activeSubscription(LocalDateTime.now());
        subscription.expire();

        // when & then
        assertThatThrownBy(() -> subscription.renew(LocalDateTime.now()))
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getErrorCode())
            .isEqualTo(ErrorCode.SUBSCRIPTION_004);
    }

    @Test
    @DisplayName("구독 만료 성공 - ACTIVE → EXPIRED")
    void expire_fromActive_success() {

        // given
        Subscription subscription = activeSubscription(LocalDateTime.now());

        // when
        subscription.expire();

        // then
        assertThat(subscription.getStatus())
            .isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("구독 만료 성공 - CANCELLED → EXPIRED")
    void expire_fromCancelled_success() {

        // given
        Subscription subscription = activeSubscription(LocalDateTime.now());
        subscription.cancel("x");

        // when
        subscription.expire();

        // then
        assertThat(subscription.getStatus())
            .isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("구독 만료 멱등 - 이미 EXPIRED여도 예외 없이 유지")
    void expire_idempotent() {

        // given
        Subscription subscription = activeSubscription(LocalDateTime.now());
        subscription.expire();

        // when
        subscription.expire();

        // then
        assertThat(subscription.getStatus())
            .isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("isValidSubscription - ACTIVE는 true")
    void isValid_active() {

        // given
        Subscription subscription = activeSubscription(LocalDateTime.now());

        // when & then
        assertThat(subscription.isValidSubscription())
            .isTrue();
    }

    @Test
    @DisplayName("isValidSubscription - CANCELLED는 true")
    void isValid_cancelled() {

        // given
        Subscription subscription = activeSubscription(LocalDateTime.now());
        subscription.cancel("x");

        // when & then
        assertThat(subscription.isValidSubscription())
            .isTrue();
    }

    @Test
    @DisplayName("isValidSubscription - EXPIRED는 false")
    void isValid_expired() {

        // given
        Subscription subscription = activeSubscription(LocalDateTime.now());
        subscription.expire();

        // when & then
        assertThat(subscription.isValidSubscription())
            .isFalse();
    }
}
