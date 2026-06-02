package com.sparta.one_stop.domain.coupon.service.issue;

import com.sparta.one_stop.domain.coupon.dto.response.IssueCouponResponse;
import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.domain.coupon.repository.UserCouponRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import com.sparta.one_stop.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LuaCouponIssueStrategyTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private LuaCouponIssueStrategy luaCouponIssueStrategy;

    @Test
    @DisplayName("supports 성공 - strategyType이 lua이면 true를 반환한다")
    void supports_success_whenStrategyTypeIsLua() {
        // when
        boolean result = luaCouponIssueStrategy.supports("lua");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("supports 성공 - strategyType이 대소문자가 달라도 lua이면 true를 반환한다")
    void supports_success_whenStrategyTypeIsLuaIgnoreCase() {
        // when
        boolean result = luaCouponIssueStrategy.supports("LUA");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("supports 성공 - strategyType이 lua가 아니면 false를 반환한다")
    void supports_success_whenStrategyTypeIsNotLua() {
        // when
        boolean result = luaCouponIssueStrategy.supports("decr");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("supports 성공 - strategyType이 null이면 false를 반환한다")
    void supports_success_whenStrategyTypeIsNull() {
        // when
        boolean result = luaCouponIssueStrategy.supports(null);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("issue 성공 - Lua Script 발급 성공 후 UserCoupon을 저장한다")
    void issue_success() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";
        String userIdValue = String.valueOf(userId);

        User user = mock(User.class);
        Coupon coupon = couponForIssueResponse(couponId);
        UserCoupon savedUserCoupon = issuedUserCoupon(
            100L,
            coupon
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue),
            anyString()
        )).thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenReturn(savedUserCoupon);
        when(couponRepository.increaseIssuedQuantity(couponId))
            .thenReturn(1);

        // when
        IssueCouponResponse result = luaCouponIssueStrategy.issue(
            userId,
            couponId
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.userCouponId()).isEqualTo(100L);
        assertThat(result.couponId()).isEqualTo(couponId);
        assertThat(result.couponName()).isEqualTo("테스트 쿠폰");
        assertThat(result.status()).isEqualTo(UserCouponStatus.AVAILABLE);

        verify(redisTemplate).execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue),
            anyString()
        );
        verify(userCouponRepository).saveAndFlush(any(UserCoupon.class));
        verify(couponRepository).increaseIssuedQuantity(couponId);
    }

    @Test
    @DisplayName("issue 실패 - userId가 null이면 인증 예외가 발생하고 Repository/Redis를 호출하지 않는다")
    void issue_fail_whenUserIdIsNull() {
        // given
        Long userId = null;
        Long couponId = 10L;

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userRepository, never()).findById(any());
        verify(couponRepository, never()).findById(any());
        verify(redisTemplate, never()).opsForValue();
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - couponId가 null이면 쿠폰 조회 예외가 발생하고 Repository/Redis를 호출하지 않는다")
    void issue_fail_whenCouponIdIsNull() {
        // given
        Long userId = 1L;
        Long couponId = null;

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userRepository, never()).findById(any());
        verify(couponRepository, never()).findById(any());
        verify(redisTemplate, never()).opsForValue();
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - 존재하지 않는 사용자면 예외가 발생한다")
    void issue_fail_whenUserNotFound() {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        when(userRepository.findById(userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userRepository).findById(userId);
        verify(couponRepository, never()).findById(any());
        verify(redisTemplate, never()).opsForValue();
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - 존재하지 않는 쿠폰이면 예외가 발생한다")
    void issue_fail_whenCouponNotFound() {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        User user = mock(User.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userRepository).findById(userId);
        verify(couponRepository).findById(couponId);
        verify(redisTemplate, never()).opsForValue();
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - 쿠폰이 비활성 또는 만료 상태이면 발급 가능 검증에서 예외가 발생한다")
    void issue_fail_whenCouponIsNotIssuable() {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        doThrow(CustomException.class)
            .when(coupon)
            .validateIssuable(any(LocalDateTime.class));

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate, never()).opsForValue();
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - Redis stock 초기화 시 DB 기준 잔여 수량이 0이면 수량 소진 예외가 발생한다")
    void issue_fail_whenInitialRemainingQuantityIsZero() {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(coupon.getTotalQuantity())
            .thenReturn(100);
        when(coupon.getIssuedQuantity())
            .thenReturn(100);

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate, never()).opsForValue();
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - Redis stock 초기화 시 TTL이 0 이하이면 만료 쿠폰 예외가 발생한다")
    void issue_fail_whenStockTtlIsZeroOrNegative() {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(coupon.getTotalQuantity())
            .thenReturn(100);
        when(coupon.getIssuedQuantity())
            .thenReturn(0);
        when(coupon.getExpiredAt())
            .thenReturn(LocalDateTime.now().minusSeconds(1));

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate, never()).opsForValue();
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - Lua Script 결과가 null이면 서비스 이용 불가 예외가 발생한다")
    void issue_fail_whenLuaResultIsNull() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";
        String userIdValue = String.valueOf(userId);

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue),
            anyString()
        )).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - Lua Script 결과가 수량 소진이면 쿠폰 소진 예외가 발생한다")
    void issue_fail_whenLuaResultIsSoldOut() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";
        String userIdValue = String.valueOf(userId);

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue),
            anyString()
        )).thenReturn(-1L);

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - Lua Script 결과가 이미 발급된 사용자이면 중복 발급 예외가 발생한다")
    void issue_fail_whenLuaResultIsAlreadyIssued() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";
        String userIdValue = String.valueOf(userId);

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue),
            anyString()
        )).thenReturn(-2L);

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - Lua Script 결과가 stock key 미초기화이면 서비스 이용 불가 예외가 발생한다")
    void issue_fail_whenLuaResultIsStockNotInitialized() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";
        String userIdValue = String.valueOf(userId);

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue),
            anyString()
        )).thenReturn(-3L);

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - DB 중복 발급이면 Redis 발급 보상 Script를 실행한다")
    void issue_fail_whenAlreadyIssuedInDb_thenCompensateRedisIssue() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";
        String userIdValue = String.valueOf(userId);

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue),
            anyString()
        )).thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(true);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue)
        )).thenReturn(1L);

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate).execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue),
            anyString()
        );
        verify(redisTemplate).execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue)
        );
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - UserCoupon 저장 중 Unique 제약 위반이 발생하면 Redis 발급 보상 Script를 실행한다")
    void issue_fail_whenDataIntegrityViolationOccurs_thenCompensateRedisIssue() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";
        String userIdValue = String.valueOf(userId);

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue),
            anyString()
        )).thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue)
        )).thenReturn(1L);

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate).execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue)
        );
    }

    @Test
    @DisplayName("issue 실패 - issuedQuantity 증가 실패 시 Redis 발급 보상 Script를 실행한다")
    void issue_fail_whenIncreaseIssuedQuantityFails_thenCompensateRedisIssue() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";
        String userIdValue = String.valueOf(userId);

        User user = mock(User.class);
        Coupon coupon = couponForIssue();
        UserCoupon savedUserCoupon = mock(UserCoupon.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue),
            anyString()
        )).thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenReturn(savedUserCoupon);
        when(couponRepository.increaseIssuedQuantity(couponId))
            .thenReturn(0);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue)
        )).thenReturn(1L);

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate).execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue)
        );
    }

    @Test
    @DisplayName("issue 실패 - 예상치 못한 RuntimeException 발생 시 Redis 발급 보상 Script를 실행한다")
    void issue_fail_whenRuntimeExceptionOccurs_thenCompensateRedisIssue() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";
        String userIdValue = String.valueOf(userId);

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue),
            anyString()
        )).thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenThrow(new RuntimeException("DB save failed"));

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue)
        )).thenReturn(1L);

        // when & then
        assertThatThrownBy(() -> luaCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(RuntimeException.class);

        verify(redisTemplate).execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey, issuedUsersKey)),
            eq(userIdValue)
        );
    }

    private Coupon couponForIssue() {
        Coupon coupon = mock(Coupon.class);

        when(coupon.getTotalQuantity())
            .thenReturn(100);
        when(coupon.getIssuedQuantity())
            .thenReturn(0);
        when(coupon.getExpiredAt())
            .thenReturn(LocalDateTime.now().plusDays(30));

        return coupon;
    }

    private Coupon couponForIssueResponse(Long couponId) {
        Coupon coupon = couponForIssue();

        when(coupon.getId())
            .thenReturn(couponId);
        when(coupon.getName())
            .thenReturn("테스트 쿠폰");

        return coupon;
    }

    private UserCoupon issuedUserCoupon(
        Long userCouponId,
        Coupon coupon
    ) {
        UserCoupon userCoupon = mock(UserCoupon.class);

        when(userCoupon.getId())
            .thenReturn(userCouponId);
        when(userCoupon.getCoupon())
            .thenReturn(coupon);
        when(userCoupon.getStatus())
            .thenReturn(UserCouponStatus.AVAILABLE);
        when(userCoupon.getCreatedAt())
            .thenReturn(LocalDateTime.now());

        return userCoupon;
    }

}
