package com.sparta.one_stop.domain.subscription.service;

import com.sparta.one_stop.domain.subscription.dto.request.CancelSubscriptionRequest;
import com.sparta.one_stop.domain.subscription.dto.response.SubscriptionResponse;
import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.domain.subscription.repository.SubscriptionRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private static final Long USER_ID = 1L;

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

    private Subscription activeSubscription() {
        LocalDateTime now = LocalDateTime.now();
        return Subscription.builder()
            .user(user())
            .startAt(now)
            .endAt(now.plusDays(30))
            .nextPaymentDate(now.plusDays(30))
            .build();
    }

    @Test
    @DisplayName("구독 신청 성공 - ACTIVE 구독 생성")
    void subscribe_success() {

        when(userRepository.findByIdForUpdate(USER_ID))
            .thenReturn(Optional.of(user()));

        when(subscriptionRepository.existsByUserIdAndStatusIn(eq(USER_ID), anyList()))
            .thenReturn(false);

        when(subscriptionRepository.save(any(Subscription.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        SubscriptionResponse response = subscriptionService.subscribe(USER_ID);

        assertThat(response.status())
            .isEqualTo(SubscriptionStatus.ACTIVE);

        verify(subscriptionRepository)
            .save(any(Subscription.class));
    }

    @Test
    @DisplayName("구독 신청 실패 - 사용자 없음")
    void subscribe_fail_userNotFound() {

        when(userRepository.findByIdForUpdate(USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            subscriptionService.subscribe(USER_ID)
        )
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getErrorCode())
            .isEqualTo(ErrorCode.MEMBER_001);

        verify(subscriptionRepository, never())
            .save(any());
    }

    @Test
    @DisplayName("구독 신청 실패 - 이미 유효 구독 존재")
    void subscribe_fail_alreadyExists() {

        when(userRepository.findByIdForUpdate(USER_ID))
            .thenReturn(Optional.of(user()));

        when(subscriptionRepository.existsByUserIdAndStatusIn(eq(USER_ID), anyList()))
            .thenReturn(true);

        assertThatThrownBy(() ->
            subscriptionService.subscribe(USER_ID)
        )
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getErrorCode())
            .isEqualTo(ErrorCode.SUBSCRIPTION_002);

        verify(subscriptionRepository, never())
            .save(any());
    }

    @Test
    @DisplayName("내 구독 조회 성공")
    void getMySubscription_success() {

        Subscription subscription = activeSubscription();
        ReflectionTestUtils.setField(subscription, "id", 10L);

        when(subscriptionRepository
            .findTopByUserIdAndStatusInOrderByCreatedAtDesc(eq(USER_ID), anyList()))
            .thenReturn(Optional.of(subscription));

        SubscriptionResponse response = subscriptionService.getMySubscription(USER_ID);

        assertThat(response.subscriptionId())
            .isEqualTo(10L);

        assertThat(response.status())
            .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("내 구독 조회 실패 - 구독 없음")
    void getMySubscription_fail_notFound() {

        when(subscriptionRepository
            .findTopByUserIdAndStatusInOrderByCreatedAtDesc(eq(USER_ID), anyList()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            subscriptionService.getMySubscription(USER_ID)
        )
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getErrorCode())
            .isEqualTo(ErrorCode.SUBSCRIPTION_001);
    }

    @Test
    @DisplayName("구독 해지 성공 - ACTIVE → CANCELLED")
    void cancelSubscription_success() {

        Subscription subscription = activeSubscription();

        when(subscriptionRepository
            .findTopByUserIdAndStatusInOrderByCreatedAtDesc(eq(USER_ID), anyList()))
            .thenReturn(Optional.of(subscription));

        SubscriptionResponse response = subscriptionService.cancelSubscription(
            USER_ID,
            new CancelSubscriptionRequest("단순 변심")
        );

        assertThat(response.status())
            .isEqualTo(SubscriptionStatus.CANCELLED);

        assertThat(subscription.getCancelReason())
            .isEqualTo("단순 변심");
    }

    @Test
    @DisplayName("구독 해지 실패 - 구독 없음")
    void cancelSubscription_fail_notFound() {

        when(subscriptionRepository
            .findTopByUserIdAndStatusInOrderByCreatedAtDesc(eq(USER_ID), anyList()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            subscriptionService.cancelSubscription(
                USER_ID,
                new CancelSubscriptionRequest("x")
            )
        )
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getErrorCode())
            .isEqualTo(ErrorCode.SUBSCRIPTION_001);
    }

    @Test
    @DisplayName("구독 해지 실패 - 이미 CANCELLED (SUBSCRIPTION_003 전파)")
    void cancelSubscription_fail_alreadyCancelled() {

        Subscription subscription = activeSubscription();
        subscription.cancel("first");

        when(subscriptionRepository
            .findTopByUserIdAndStatusInOrderByCreatedAtDesc(eq(USER_ID), anyList()))
            .thenReturn(Optional.of(subscription));

        assertThatThrownBy(() ->
            subscriptionService.cancelSubscription(
                USER_ID,
                new CancelSubscriptionRequest("second")
            )
        )
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getErrorCode())
            .isEqualTo(ErrorCode.SUBSCRIPTION_003);
    }

    @Test
    @DisplayName("hasActiveSubscription - 있으면 true")
    void hasActiveSubscription_true() {

        when(subscriptionRepository.existsByUserIdAndStatusIn(eq(USER_ID), anyList()))
            .thenReturn(true);

        assertThat(subscriptionService.hasActiveSubscription(USER_ID))
            .isTrue();
    }

    @Test
    @DisplayName("hasActiveSubscription - 없으면 false")
    void hasActiveSubscription_false() {

        when(subscriptionRepository.existsByUserIdAndStatusIn(eq(USER_ID), anyList()))
            .thenReturn(false);

        assertThat(subscriptionService.hasActiveSubscription(USER_ID))
            .isFalse();
    }
}
