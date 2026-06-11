package com.sparta.one_stop.domain.subscription.service;

import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.domain.subscription.repository.SubscriptionRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionSchedulerServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionSchedulerService subscriptionSchedulerService;

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

    private Subscription active(LocalDateTime now) {
        return Subscription.builder()
            .user(user())
            .startAt(now.minusDays(30))
            .endAt(now)
            .nextPaymentDate(now)
            .build();
    }

    @Test
    @DisplayName("자동결제 성공 - 대상 ACTIVE 구독은 renew되어 30일 연장")
    void processAutoPayment_renew() {

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = active(now);

        when(subscriptionRepository.findAllByStatusAndNextPaymentDateLessThanEqual(
            eq(SubscriptionStatus.ACTIVE),
            any(LocalDateTime.class),
            any(Pageable.class)
        ))
            .thenReturn(List.of(subscription), Collections.emptyList());

        subscriptionSchedulerService.processAutoPayment();

        assertThat(subscription.getStatus())
            .isEqualTo(SubscriptionStatus.ACTIVE);

        assertThat(subscription.getEndAt())
            .isEqualTo(now.plusDays(30));

        assertThat(subscription.getNextPaymentDate())
            .isEqualTo(now.plusDays(30));
    }

    @Test
    @DisplayName("자동결제 실패(비ACTIVE) - 해당 구독은 EXPIRED 처리")
    void processAutoPayment_expireOnFailure() {

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = active(now);
        subscription.cancel("해지");

        when(subscriptionRepository.findAllByStatusAndNextPaymentDateLessThanEqual(
            eq(SubscriptionStatus.ACTIVE),
            any(LocalDateTime.class),
            any(Pageable.class)
        ))
            .thenReturn(List.of(subscription), Collections.emptyList());

        subscriptionSchedulerService.processAutoPayment();

        assertThat(subscription.getStatus())
            .isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("자동결제 - 대상이 없으면 아무 처리 없이 종료")
    void processAutoPayment_empty() {

        when(subscriptionRepository.findAllByStatusAndNextPaymentDateLessThanEqual(
            eq(SubscriptionStatus.ACTIVE),
            any(LocalDateTime.class),
            any(Pageable.class)
        ))
            .thenReturn(Collections.emptyList());

        subscriptionSchedulerService.processAutoPayment();
    }
}
