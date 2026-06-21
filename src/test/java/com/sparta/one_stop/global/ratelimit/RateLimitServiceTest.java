package com.sparta.one_stop.global.ratelimit;

import com.sparta.one_stop.global.enums.ratelimit.RateLimitPolicy;
import com.sparta.one_stop.global.audit.SecurityAuditService;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitServiceTest {

    private final RedisTemplate<String, String> redisTemplate =
        mock(RedisTemplate.class);

    private final RateLimitService rateLimitService =
        new RateLimitService(redisTemplate, mock(SecurityAuditService.class));

    @ParameterizedTest
    @EnumSource(
        value = RateLimitPolicy.class,
        names = {
            "PAYMENT_APPROVE_PER_USER",
            "ORDER_CREATE_PER_USER",
            "COUPON_ISSUE_PER_USER"
        }
    )
    @DisplayName("tryConsume 성공 - 정책별 제한 횟수 이내이면 정상 통과한다")
    void tryConsume_success_whenWithinLimit(RateLimitPolicy policy) {
        // given
        String identifier = "1";
        String redisKey = policy.buildKey(identifier);

        when(redisTemplate.execute(
            any(RedisScript.class),
            eq(List.of(redisKey)),
            eq(String.valueOf(policy.getWindowSeconds())),
            eq(String.valueOf(policy.getLimit()))
        )).thenReturn(1L);

        // when & then
        assertThatCode(() -> rateLimitService.tryConsume(
            policy,
            identifier
        )).doesNotThrowAnyException();

        verify(redisTemplate).execute(
            any(RedisScript.class),
            eq(List.of(redisKey)),
            eq(String.valueOf(policy.getWindowSeconds())),
            eq(String.valueOf(policy.getLimit()))
        );
    }

    @ParameterizedTest
    @EnumSource(
        value = RateLimitPolicy.class,
        names = {
            "PAYMENT_APPROVE_PER_USER",
            "ORDER_CREATE_PER_USER",
            "COUPON_ISSUE_PER_USER"
        }
    )
    @DisplayName("tryConsume 실패 - 정책별 제한 횟수를 초과하면 COMMON_009 예외가 발생한다")
    void tryConsume_fail_whenLimitExceeded(RateLimitPolicy policy) {
        // given
        String identifier = "1";
        String redisKey = policy.buildKey(identifier);

        when(redisTemplate.execute(
            any(RedisScript.class),
            eq(List.of(redisKey)),
            eq(String.valueOf(policy.getWindowSeconds())),
            eq(String.valueOf(policy.getLimit()))
        )).thenReturn(-55L);

        // when & then
        assertThatThrownBy(() -> rateLimitService.tryConsume(
            policy,
            identifier
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_009);
            });
    }

    @Test
    @DisplayName("tryConsume 성공 - Redis 장애가 발생하면 Fail-Open으로 예외 없이 통과한다")
    void tryConsume_success_whenRedisConnectionFails() {
        // given
        RateLimitPolicy policy = RateLimitPolicy.PAYMENT_APPROVE_PER_USER;
        String identifier = "1";
        String redisKey = policy.buildKey(identifier);

        when(redisTemplate.execute(
            any(RedisScript.class),
            eq(List.of(redisKey)),
            eq(String.valueOf(policy.getWindowSeconds())),
            eq(String.valueOf(policy.getLimit()))
        )).thenThrow(new RedisConnectionFailureException("Redis connection failed"));

        // when & then
        assertThatCode(() -> rateLimitService.tryConsume(
            policy,
            identifier
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("tryConsume 성공 - RedisSystemException 발생 시 Fail-Open으로 예외 없이 통과한다")
    void tryConsume_success_whenRedisSystemExceptionOccurs() {
        // given
        RateLimitPolicy policy = RateLimitPolicy.PAYMENT_APPROVE_PER_USER;
        String identifier = "1";
        String redisKey = policy.buildKey(identifier);

        when(redisTemplate.execute(
            any(RedisScript.class),
            eq(List.of(redisKey)),
            eq(String.valueOf(policy.getWindowSeconds())),
            eq(String.valueOf(policy.getLimit()))
        )).thenThrow(new RedisSystemException(
            "Redis system error",
            new RuntimeException("redis down")
        ));

        // when & then
        assertThatCode(() -> rateLimitService.tryConsume(
            policy,
            identifier
        )).doesNotThrowAnyException();

        verify(redisTemplate).execute(
            any(RedisScript.class),
            eq(List.of(redisKey)),
            eq(String.valueOf(policy.getWindowSeconds())),
            eq(String.valueOf(policy.getLimit()))
        );
    }

    @Test
    @DisplayName("isAllowed 성공 - 제한 횟수 이내이면 true를 반환한다")
    void isAllowed_success_whenWithinLimit() {
        // given
        RateLimitPolicy policy = RateLimitPolicy.PAYMENT_APPROVE_PER_USER;
        String identifier = "1";
        String redisKey = policy.buildKey(identifier);

        when(redisTemplate.execute(
            any(RedisScript.class),
            eq(List.of(redisKey)),
            eq(String.valueOf(policy.getWindowSeconds())),
            eq(String.valueOf(policy.getLimit()))
        )).thenReturn(1L);

        // when
        boolean result = rateLimitService.isAllowed(
            policy,
            identifier
        );

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isAllowed 성공 - RedisSystemException 발생 시 Fail-Open으로 true를 반환한다")
    void isAllowed_success_returnTrue_whenRedisSystemExceptionOccurs() {
        // given
        RateLimitPolicy policy = RateLimitPolicy.PAYMENT_APPROVE_PER_USER;
        String identifier = "1";
        String redisKey = policy.buildKey(identifier);

        when(redisTemplate.execute(
            any(RedisScript.class),
            eq(List.of(redisKey)),
            eq(String.valueOf(policy.getWindowSeconds())),
            eq(String.valueOf(policy.getLimit()))
        )).thenThrow(new RedisSystemException(
            "Redis system error",
            new RuntimeException("redis down")
        ));

        // when
        boolean result = rateLimitService.isAllowed(
            policy,
            identifier
        );

        // then
        assertThat(result).isTrue();

        verify(redisTemplate).execute(
            any(RedisScript.class),
            eq(List.of(redisKey)),
            eq(String.valueOf(policy.getWindowSeconds())),
            eq(String.valueOf(policy.getLimit()))
        );
    }

    @Test
    @DisplayName("isAllowed 성공 - Redis 연결 장애 발생 시 Fail-Open으로 true를 반환한다")
    void isAllowed_success_returnTrue_whenRedisConnectionFails() {
        // given
        RateLimitPolicy policy = RateLimitPolicy.PAYMENT_APPROVE_PER_USER;
        String identifier = "1";
        String redisKey = policy.buildKey(identifier);

        when(redisTemplate.execute(
            any(RedisScript.class),
            eq(List.of(redisKey)),
            eq(String.valueOf(policy.getWindowSeconds())),
            eq(String.valueOf(policy.getLimit()))
        )).thenThrow(new RedisConnectionFailureException("Redis connection failed"));

        // when
        boolean result = rateLimitService.isAllowed(
            policy,
            identifier
        );

        // then
        assertThat(result).isTrue();

        verify(redisTemplate).execute(
            any(RedisScript.class),
            eq(List.of(redisKey)),
            eq(String.valueOf(policy.getWindowSeconds())),
            eq(String.valueOf(policy.getLimit()))
        );
    }

    @Test
    @DisplayName("isAllowed 실패 - 제한 횟수를 초과하면 false를 반환한다")
    void isAllowed_fail_whenLimitExceeded() {
        // given
        RateLimitPolicy policy = RateLimitPolicy.PAYMENT_APPROVE_PER_USER;
        String identifier = "1";
        String redisKey = policy.buildKey(identifier);

        when(redisTemplate.execute(
            any(RedisScript.class),
            eq(List.of(redisKey)),
            eq(String.valueOf(policy.getWindowSeconds())),
            eq(String.valueOf(policy.getLimit()))
        )).thenReturn(-55L);

        // when
        boolean result = rateLimitService.isAllowed(
            policy,
            identifier
        );

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isAllowed 실패 - COMMON_009가 아닌 CustomException은 그대로 전파한다")
    void isAllowed_fail_whenCustomExceptionIsNotRateLimitException() {
        // given
        RateLimitPolicy policy = RateLimitPolicy.PAYMENT_APPROVE_PER_USER;
        String identifier = "1";

        RateLimitService spyRateLimitService = spy(rateLimitService);

        CustomException customException = new CustomException(ErrorCode.COMMON_010);

        doThrow(customException)
            .when(spyRateLimitService)
            .tryConsume(
                policy,
                identifier
            );

        // when & then
        assertThatThrownBy(() -> spyRateLimitService.isAllowed(
            policy,
            identifier
        ))
            .isSameAs(customException);

        verify(spyRateLimitService).tryConsume(
            policy,
            identifier
        );
    }

}
