package com.sparta.one_stop.domain.subscription.service;

import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.domain.subscription.repository.SubscriptionRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirationSchedulerServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionExpirationSchedulerService expirationSchedulerService;

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

    private Subscription cancelledExpired(LocalDateTime now) {
        Subscription subscription = Subscription.builder()
            .user(user())
            .startAt(now.minusDays(40))
            .endAt(now.minusDays(1))
            .nextPaymentDate(now.minusDays(1))
            .build();
        subscription.cancel("해지");
        return subscription;
    }

    @Test
    @DisplayName("만료 처리 성공 - 조회된 CANCELLED 구독을 모두 EXPIRED로 변경")
    void processExpiration_expiresAll() {

        LocalDateTime now = LocalDateTime.now();
        Subscription s1 = cancelledExpired(now);
        Subscription s2 = cancelledExpired(now);

        when(subscriptionRepository.findAllByStatusAndEndAtBefore(
            eq(SubscriptionStatus.CANCELLED),
            any(LocalDateTime.class),
            any(Pageable.class)
        ))
            .thenReturn(List.of(s1, s2), Collections.emptyList());

        expirationSchedulerService.processExpiration();

        assertThat(s1.getStatus())
            .isEqualTo(SubscriptionStatus.EXPIRED);

        assertThat(s2.getStatus())
            .isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    @DisplayName("만료 처리 버그검증 - chunk 반복 시 항상 page 0 조회 (page++로 행 누락 방지)")
    void processExpiration_alwaysQueriesPageZero() {

        LocalDateTime now = LocalDateTime.now();

        when(subscriptionRepository.findAllByStatusAndEndAtBefore(
            eq(SubscriptionStatus.CANCELLED),
            any(LocalDateTime.class),
            any(Pageable.class)
        ))
            .thenReturn(
                List.of(cancelledExpired(now)),
                List.of(cancelledExpired(now)),
                Collections.emptyList()
            );

        expirationSchedulerService.processExpiration();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        verify(subscriptionRepository, times(3))
            .findAllByStatusAndEndAtBefore(
                eq(SubscriptionStatus.CANCELLED),
                any(LocalDateTime.class),
                pageableCaptor.capture()
            );

        assertThat(pageableCaptor.getAllValues())
            .allMatch(pageable -> pageable.getPageNumber() == 0);
    }

    @Test
    @DisplayName("만료 처리 - 대상이 없으면 아무 처리 없이 종료")
    void processExpiration_empty() {

        when(subscriptionRepository.findAllByStatusAndEndAtBefore(
            eq(SubscriptionStatus.CANCELLED),
            any(LocalDateTime.class),
            any(Pageable.class)
        ))
            .thenReturn(Collections.emptyList());

        expirationSchedulerService.processExpiration();
    }
}
