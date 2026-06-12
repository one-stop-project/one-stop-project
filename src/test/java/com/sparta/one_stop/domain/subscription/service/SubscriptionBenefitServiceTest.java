package com.sparta.one_stop.domain.subscription.service;

import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.domain.subscription.repository.SubscriptionRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionBenefitServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionBenefitService subscriptionBenefitService;

    private static final Long USER_ID = 1L;
    private static final Long TOTAL_PRICE = 10_000L;
    private static final long EXPECTED_DISCOUNT = 500L; // 10,000 * 0.05

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

    private Subscription active() {
        LocalDateTime now = LocalDateTime.now();
        return Subscription.builder()
            .user(user())
            .startAt(now)
            .endAt(now.plusDays(30))
            .nextPaymentDate(now.plusDays(30))
            .build();
    }

    private Subscription cancelled(LocalDateTime endAt) {
        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = Subscription.builder()
            .user(user())
            .startAt(now.minusDays(10))
            .endAt(endAt)
            .nextPaymentDate(endAt)
            .build();
        subscription.cancel("해지");
        return subscription;
    }

    private Subscription expired() {
        Subscription subscription = active();
        subscription.expire();
        return subscription;
    }

    private void mockFindResult(Subscription subscription) {
        when(subscriptionRepository
            .findTopByUserIdAndStatusInOrderByCreatedAtDesc(eq(USER_ID), anyList()))
            .thenReturn(Optional.ofNullable(subscription));
    }

    @Test
    @DisplayName("할인 계산 - 구독 없으면 0")
    void calculateDiscount_noSubscription() {

        mockFindResult(null);

        assertThat(subscriptionBenefitService.calculateDiscount(USER_ID, TOTAL_PRICE))
            .isZero();
    }

    @Test
    @DisplayName("할인 계산 - ACTIVE면 5% 할인")
    void calculateDiscount_active() {

        mockFindResult(active());

        assertThat(subscriptionBenefitService.calculateDiscount(USER_ID, TOTAL_PRICE))
            .isEqualTo(EXPECTED_DISCOUNT);
    }

    @Test
    @DisplayName("할인 계산 - CANCELLED라도 endAt 남았으면 5% 할인")
    void calculateDiscount_cancelledBeforeEndAt() {

        mockFindResult(cancelled(LocalDateTime.now().plusDays(5)));

        assertThat(subscriptionBenefitService.calculateDiscount(USER_ID, TOTAL_PRICE))
            .isEqualTo(EXPECTED_DISCOUNT);
    }

    @Test
    @DisplayName("할인 계산 - CANCELLED이고 endAt 지났으면 0 (스케줄러 미반영 누수 방지)")
    void calculateDiscount_cancelledAfterEndAt() {

        mockFindResult(cancelled(LocalDateTime.now().minusDays(1)));

        assertThat(subscriptionBenefitService.calculateDiscount(USER_ID, TOTAL_PRICE))
            .isZero();
    }

    @Test
    @DisplayName("할인 계산 - EXPIRED면 0")
    void calculateDiscount_expired() {

        mockFindResult(expired());

        assertThat(subscriptionBenefitService.calculateDiscount(USER_ID, TOTAL_PRICE))
            .isZero();
    }

    @Test
    @DisplayName("무료배송 - ACTIVE면 대상")
    void isFreeShippingEligible_active() {

        mockFindResult(active());

        assertThat(subscriptionBenefitService.isFreeShippingEligible(USER_ID))
            .isTrue();
    }

    @Test
    @DisplayName("무료배송 - CANCELLED라도 endAt 남았으면 대상")
    void isFreeShippingEligible_cancelledBeforeEndAt() {

        mockFindResult(cancelled(LocalDateTime.now().plusDays(5)));

        assertThat(subscriptionBenefitService.isFreeShippingEligible(USER_ID))
            .isTrue();
    }

    @Test
    @DisplayName("무료배송 - CANCELLED이고 endAt 지났으면 비대상 (스케줄러 미반영 누수 방지)")
    void isFreeShippingEligible_cancelledAfterEndAt() {

        mockFindResult(cancelled(LocalDateTime.now().minusDays(1)));

        assertThat(subscriptionBenefitService.isFreeShippingEligible(USER_ID))
            .isFalse();
    }

    @Test
    @DisplayName("무료배송 - EXPIRED면 비대상")
    void isFreeShippingEligible_expired() {

        mockFindResult(expired());

        assertThat(subscriptionBenefitService.isFreeShippingEligible(USER_ID))
            .isFalse();
    }

    @Test
    @DisplayName("무료배송 - 구독 없으면 비대상")
    void isFreeShippingEligible_noSubscription() {

        mockFindResult(null);

        assertThat(subscriptionBenefitService.isFreeShippingEligible(USER_ID))
            .isFalse();
    }
}
