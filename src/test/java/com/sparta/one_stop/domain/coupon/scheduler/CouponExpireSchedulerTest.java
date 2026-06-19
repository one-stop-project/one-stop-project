package com.sparta.one_stop.domain.coupon.scheduler;

import com.sparta.one_stop.domain.coupon.service.CouponExpireService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponExpireSchedulerTest {

    @Mock
    private CouponExpireService couponExpireService;

    @InjectMocks
    private CouponExpireScheduler couponExpireScheduler;

    @Test
    @DisplayName("runDailyExpiration 성공 - CouponExpireService.expireAll을 현재 시각으로 호출한다")
    void runDailyExpiration_success_callsExpireAll() {
        // when
        couponExpireScheduler.runDailyExpiration();

        // then
        verify(couponExpireService).expireAll(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("runDailyExpiration 성공 - expireAll에서 예외가 발생해도 스케줄러가 중단되지 않는다")
    void runDailyExpiration_success_exceptionIsCaught() {
        // given
        doThrow(new RuntimeException("DB 오류"))
            .when(couponExpireService).expireAll(any(LocalDateTime.class));

        // when & then
        assertThatNoException().isThrownBy(() -> couponExpireScheduler.runDailyExpiration());

        verify(couponExpireService).expireAll(any(LocalDateTime.class));
    }
}
