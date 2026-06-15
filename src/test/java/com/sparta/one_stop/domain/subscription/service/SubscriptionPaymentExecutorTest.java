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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionPaymentExecutorTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionPaymentExecutor paymentExecutor;

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

    private Subscription active(LocalDateTime now, Long id) {
        Subscription subscription = Subscription.builder()
            .user(user())
            .startAt(now.minusDays(30))
            .endAt(now)
            .nextPaymentDate(now)
            .build();
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }

    @Test
    @DisplayName("자동결제 성공 - renew 되어 30일 연장")
    void processChunk_renew() {

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = active(now, 1L);

        when(subscriptionRepository.findAllById(List.of(1L)))
            .thenReturn(List.of(subscription));

        paymentExecutor.processChunk(List.of(1L), now);

        assertThat(subscription.getStatus())
            .isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getEndAt())
            .isEqualTo(now.plusDays(30));
        assertThat(subscription.getNextPaymentDate())
            .isEqualTo(now.plusDays(30));
    }

    @Test
    @DisplayName("자동결제 실패 - CANCELLED 구독은 renew 실패하여 EXPIRED 처리")
    void processChunk_expireOnFailure() {

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = active(now, 1L);
        subscription.cancel("해지");

        when(subscriptionRepository.findAllById(List.of(1L)))
            .thenReturn(List.of(subscription));

        paymentExecutor.processChunk(List.of(1L), now);

        assertThat(subscription.getStatus())
            .isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("다중결제 방지 - nextPaymentDate가 now 이후면 스킵")
    void processChunk_skipAlreadyRenewed() {

        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = active(now, 1L);
        // 이미 갱신된 상태 시뮬레이션
        subscription.renew();

        when(subscriptionRepository.findAllById(List.of(1L)))
            .thenReturn(List.of(subscription));

        paymentExecutor.processChunk(List.of(1L), now);

        // renew가 또 호출되지 않으므로 60일이 아닌 30일 연장 상태 유지
        assertThat(subscription.getNextPaymentDate())
            .isEqualTo(now.plusDays(30));
    }
}
