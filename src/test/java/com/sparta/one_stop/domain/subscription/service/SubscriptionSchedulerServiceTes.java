package com.sparta.one_stop.domain.subscription.service;

import com.sparta.one_stop.domain.subscription.repository.SubscriptionRepository;
import com.sparta.one_stop.global.enums.subscription.SubscriptionStatus;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionSchedulerServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionPaymentExecutor paymentExecutor;

    @InjectMocks
    private SubscriptionSchedulerService subscriptionSchedulerService;

    @Test
    @DisplayName("자동결제 - 대상 ID를 조회하여 executor에 위임")
    void processAutoPayment_delegatesToExecutor() {

        List<Long> ids = List.of(1L, 2L, 3L);

        when(subscriptionRepository.findIdsByStatusAndNextPaymentDateLessThanEqual(
            eq(SubscriptionStatus.ACTIVE),
            any(LocalDateTime.class),
            any(Pageable.class)
        ))
            .thenReturn(ids, Collections.emptyList());

        subscriptionSchedulerService.processAutoPayment();

        verify(paymentExecutor, times(1))
            .processChunk(eq(ids), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("자동결제 - 대상이 없으면 executor 호출 없이 종료")
    void processAutoPayment_empty() {

        when(subscriptionRepository.findIdsByStatusAndNextPaymentDateLessThanEqual(
            eq(SubscriptionStatus.ACTIVE),
            any(LocalDateTime.class),
            any(Pageable.class)
        ))
            .thenReturn(Collections.emptyList());

        subscriptionSchedulerService.processAutoPayment();

        verify(paymentExecutor, never())
            .processChunk(any(), any());
    }

    @Test
    @DisplayName("자동결제 - chunk 2회 반복 시 executor 2회 호출")
    void processAutoPayment_multipleChunks() {

        List<Long> chunk1 = List.of(1L, 2L);
        List<Long> chunk2 = List.of(3L, 4L);

        when(subscriptionRepository.findIdsByStatusAndNextPaymentDateLessThanEqual(
            eq(SubscriptionStatus.ACTIVE),
            any(LocalDateTime.class),
            any(Pageable.class)
        ))
            .thenReturn(chunk1, chunk2, Collections.emptyList());

        subscriptionSchedulerService.processAutoPayment();

        verify(paymentExecutor, times(2))
            .processChunk(any(), any(LocalDateTime.class));
    }
}
