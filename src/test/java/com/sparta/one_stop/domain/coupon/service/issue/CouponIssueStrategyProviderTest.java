package com.sparta.one_stop.domain.coupon.service.issue;

import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponIssueStrategyProviderTest {

    @Mock
    private CouponIssueStrategy decrStrategy;

    @Mock
    private CouponIssueStrategy luaStrategy;

    @Mock
    private CouponIssueStrategy lockStrategy;

    private CouponIssueStrategyProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CouponIssueStrategyProvider(
            List.of(
                decrStrategy,
                luaStrategy,
                lockStrategy
            )
        );
    }

    @Test
    @DisplayName("getStrategy 성공 - 설정값에 맞는 쿠폰 발급 전략을 반환한다")
    void getStrategy_success_whenStrategyTypeMatches() {
        // given
        ReflectionTestUtils.setField(
            provider,
            "strategyType",
            "lua"
        );

        when(decrStrategy.supports("lua"))
            .thenReturn(false);
        when(luaStrategy.supports("lua"))
            .thenReturn(true);

        // when
        CouponIssueStrategy result = provider.getStrategy();

        // then
        assertThat(result).isSameAs(luaStrategy);

        verify(decrStrategy).supports("lua");
        verify(luaStrategy).supports("lua");
        verify(lockStrategy, never()).supports("lua");
    }

    @Test
    @DisplayName("getStrategy 실패 - 설정값에 맞는 쿠폰 발급 전략이 없으면 예외 발생")
    void getStrategy_fail_whenUnsupportedStrategyType() {
        // given
        ReflectionTestUtils.setField(
            provider,
            "strategyType",
            "unsupported"
        );

        when(decrStrategy.supports("unsupported"))
            .thenReturn(false);
        when(luaStrategy.supports("unsupported"))
            .thenReturn(false);
        when(lockStrategy.supports("unsupported"))
            .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> provider.getStrategy())
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.COUPON_009);

        verify(decrStrategy).supports("unsupported");
        verify(luaStrategy).supports("unsupported");
        verify(lockStrategy).supports("unsupported");
    }

}
