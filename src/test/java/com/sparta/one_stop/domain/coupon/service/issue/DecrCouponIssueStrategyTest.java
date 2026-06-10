package com.sparta.one_stop.domain.coupon.service.issue;

import com.sparta.one_stop.domain.coupon.dto.response.IssueCouponResponse;
import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.domain.coupon.repository.UserCouponRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.coupon.CouponStatus;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecrCouponIssueStrategyTest {

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

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private DecrCouponIssueStrategy decrCouponIssueStrategy;

    @BeforeEach
    void setUpTransactionSynchronization() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.initSynchronization();
        }
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("supports 성공 - strategyType이 decr이면 true를 반환한다")
    void supports_success_whenStrategyTypeIsDecr() {
        // when
        boolean result = decrCouponIssueStrategy.supports("decr");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("supports 성공 - strategyType이 대소문자가 달라도 decr이면 true를 반환한다")
    void supports_success_whenStrategyTypeIsDecrIgnoreCase() {
        // when
        boolean result = decrCouponIssueStrategy.supports("DECR");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("supports 성공 - strategyType이 decr이 아니면 false를 반환한다")
    void supports_success_whenStrategyTypeIsNotDecr() {
        // when
        boolean result = decrCouponIssueStrategy.supports("lock");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("supports 성공 - strategyType이 null이면 false를 반환한다")
    void supports_success_whenStrategyTypeIsNull() {
        // when
        boolean result = decrCouponIssueStrategy.supports(null);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("issue 성공 - Redis 수량 차감 후 UserCoupon을 저장하고 발급 사용자 Set에 기록한다")
    void issue_success() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";

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

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(setOperations.isMember(issuedUsersKey, String.valueOf(userId)))
            .thenReturn(false);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        )).thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenReturn(savedUserCoupon);
        when(couponRepository.increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        )).thenReturn(1);

        when(setOperations.add(issuedUsersKey, String.valueOf(userId)))
            .thenReturn(1L);
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            anyList(),
            anyString()
        )).thenReturn(1L);

        // when
        IssueCouponResponse result = decrCouponIssueStrategy.issue(
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
            eq(List.of(stockKey))
        );
        verify(userCouponRepository).saveAndFlush(any(UserCoupon.class));
        verify(couponRepository).increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        );
        verify(setOperations).add(issuedUsersKey, String.valueOf(userId));
        verify(redisTemplate).execute(
            any(DefaultRedisScript.class),
            eq(List.of(issuedUsersKey)),
            anyString()
        );
        verify(valueOperations, never()).increment(stockKey);
        verify(setOperations, never()).remove(anyString(), anyString());
    }

    @Test
    @DisplayName("issue 성공 - Redis stock key가 TTL 만료로 사라지면 DB 기준 재초기화 후 1회 재시도한다")
    void issue_success_whenStockKeyNotFound_thenReinitializeAndRetry() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";
        String userIdValue = String.valueOf(userId);

        User user = mock(User.class);
        Coupon coupon = couponForIssueResponse(couponId);
        UserCoupon savedUserCoupon = issuedUserCoupon(100L, coupon);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        // 첫 번째 조회: 최초 발급 가능 검증 및 초기화
        // 두 번째 조회: stock key 재초기화 시 최신 Coupon 조회
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(setOperations.isMember(issuedUsersKey, userIdValue))
            .thenReturn(false);

        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);

        // 첫 번째 안전 DECR 결과: -3(stock key 없음)
        // 재초기화 후 두 번째 안전 DECR 결과: 99(정상 차감)
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        )).thenReturn(-3L, 99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenReturn(savedUserCoupon);
        when(couponRepository.increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        )).thenReturn(1);

        when(setOperations.add(issuedUsersKey, userIdValue))
            .thenReturn(1L);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(issuedUsersKey)),
            anyString()
        )).thenReturn(1L);

        // when
        IssueCouponResponse result = decrCouponIssueStrategy.issue(
            userId,
            couponId
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.userCouponId()).isEqualTo(100L);
        assertThat(result.couponId()).isEqualTo(couponId);

        // 최초 초기화 + 재초기화
        verify(valueOperations, times(2)).setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        );

        // 비정상 stock key 정리
        verify(redisTemplate).delete(stockKey);

        // 안전 DECR 2회: -3 감지 후 재시도
        verify(redisTemplate, times(2)).execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        );

        verify(couponRepository, times(2)).findById(couponId);
        verify(userCouponRepository).saveAndFlush(any(UserCoupon.class));
        verify(couponRepository).increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        );
        verify(setOperations).add(issuedUsersKey, userIdValue);

        verify(valueOperations, never()).increment(stockKey);
        verify(setOperations, never()).remove(anyString(), anyString());
    }

    @Test
    @DisplayName("issue 성공 - Redis stock key에 TTL이 없으면 삭제 후 DB 기준 재초기화하고 1회 재시도한다")
    void issue_success_whenStockKeyHasNoTtl_thenDeleteReinitializeAndRetry() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";
        String userIdValue = String.valueOf(userId);

        User user = mock(User.class);
        Coupon coupon = couponForIssueResponse(couponId);
        UserCoupon savedUserCoupon = issuedUserCoupon(100L, coupon);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        // 첫 번째 조회: 최초 발급 가능 검증 및 초기화
        // 두 번째 조회: TTL 없는 stock key 복구 시 최신 Coupon 조회
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(setOperations.isMember(issuedUsersKey, userIdValue))
            .thenReturn(false);

        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);

        // 첫 번째 안전 DECR 결과: -4(TTL 없는 비정상 key)
        // 재초기화 후 두 번째 안전 DECR 결과: 99(정상 차감)
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        )).thenReturn(-4L, 99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenReturn(savedUserCoupon);
        when(couponRepository.increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        )).thenReturn(1);

        when(setOperations.add(issuedUsersKey, userIdValue))
            .thenReturn(1L);

        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(issuedUsersKey)),
            anyString()
        )).thenReturn(1L);

        // when
        IssueCouponResponse result = decrCouponIssueStrategy.issue(
            userId,
            couponId
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.userCouponId()).isEqualTo(100L);
        assertThat(result.couponId()).isEqualTo(couponId);

        // 최초 초기화 + 재초기화
        verify(valueOperations, times(2)).setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        );

        // TTL 없는 0 또는 음수 key 제거
        verify(redisTemplate).delete(stockKey);

        // 안전 DECR 2회: -4 감지 후 재시도
        verify(redisTemplate, times(2)).execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        );

        verify(couponRepository, times(2)).findById(couponId);
        verify(userCouponRepository).saveAndFlush(any(UserCoupon.class));
        verify(couponRepository).increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        );
        verify(setOperations).add(issuedUsersKey, userIdValue);

        verify(valueOperations, never()).increment(stockKey);
        verify(setOperations, never()).remove(anyString(), anyString());
    }

    @Test
    @DisplayName("issue 실패 - userId가 null이면 인증 예외가 발생하고 Repository/Redis를 호출하지 않는다")
    void issue_fail_whenUserIdIsNull() {
        // given
        Long userId = null;
        Long couponId = 10L;

        // when & then
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userRepository, never()).findById(any());
        verify(couponRepository, never()).findById(any());
        verify(redisTemplate, never()).opsForSet();
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
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userRepository, never()).findById(any());
        verify(couponRepository, never()).findById(any());
        verify(redisTemplate, never()).opsForSet();
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
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userRepository).findById(userId);
        verify(couponRepository, never()).findById(any());
        verify(redisTemplate, never()).opsForSet();
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
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userRepository).findById(userId);
        verify(couponRepository).findById(couponId);
        verify(redisTemplate, never()).opsForSet();
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
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).opsForSet();
    }

    @Test
    @DisplayName("issue 실패 - Redis Set에 이미 발급 사용자로 기록되어 있으면 중복 발급 예외가 발생한다")
    void issue_fail_whenAlreadyIssuedInRedis() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String issuedUsersKey = "coupon:issued-users:10";

        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(setOperations.isMember(issuedUsersKey, String.valueOf(userId)))
            .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).execute(
            any(DefaultRedisScript.class),
            anyList()
        );
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - Redis stock 초기화 시 DB 기준 잔여 수량이 0이면 수량 소진 예외가 발생한다")
    void issue_fail_whenInitialRemainingQuantityIsZero() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String issuedUsersKey = "coupon:issued-users:10";

        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(setOperations.isMember(issuedUsersKey, String.valueOf(userId)))
            .thenReturn(false);

        when(coupon.getTotalQuantity())
            .thenReturn(100);
        when(coupon.getIssuedQuantity())
            .thenReturn(100);

        // when & then
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate, never()).opsForValue();
        verify(valueOperations, never()).setIfAbsent(
            anyString(),
            anyString(),
            any(Duration.class)
        );
        verify(redisTemplate, never()).execute(
            any(DefaultRedisScript.class),
            anyList()
        );
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - Redis stock 초기화 시 TTL이 0 이하이면 만료 쿠폰 예외가 발생한다")
    void issue_fail_whenStockTtlIsZeroOrNegative() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String issuedUsersKey = "coupon:issued-users:10";

        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(setOperations.isMember(issuedUsersKey, String.valueOf(userId)))
            .thenReturn(false);

        when(coupon.getTotalQuantity())
            .thenReturn(100);
        when(coupon.getIssuedQuantity())
            .thenReturn(0);
        when(coupon.getExpiredAt())
            .thenReturn(LocalDateTime.now().minusSeconds(1));

        // when & then
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate, never()).opsForValue();
        verify(valueOperations, never()).setIfAbsent(
            anyString(),
            anyString(),
            any(Duration.class)
        );
        verify(redisTemplate, never()).execute(
            any(DefaultRedisScript.class),
            anyList()
        );
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - Redis DECR 결과가 음수이면 수량 소진 예외가 발생하고 Redis 수량을 보상 증가한다")
    void issue_fail_whenRedisStockIsSoldOut() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(setOperations.isMember(issuedUsersKey, String.valueOf(userId)))
            .thenReturn(false);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        )).thenReturn(-1L);

        // when & then
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(valueOperations).increment(stockKey);
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
        verify(setOperations, never()).add(anyString(), anyString());
    }

    @Test
    @DisplayName("issue 실패 - Redis DECR 결과가 null이면 서비스 이용 불가 예외가 발생한다")
    void issue_fail_whenRedisDecrementReturnsNull() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(setOperations.isMember(issuedUsersKey, String.valueOf(userId)))
            .thenReturn(false);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        )).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate).execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        );
        verify(valueOperations, never()).increment(stockKey);
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
        verify(setOperations, never()).add(anyString(), anyString());
    }

    @Test
    @DisplayName("issue 실패 - DB 중복 발급이면 Redis 수량을 보상 증가하고 중복 발급 예외가 발생한다")
    void issue_fail_whenAlreadyIssuedInDb() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(setOperations.isMember(issuedUsersKey, String.valueOf(userId)))
            .thenReturn(false);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        )).thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(valueOperations).increment(stockKey);
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - UserCoupon 저장 중 Unique 제약 위반이 발생하면 Redis 수량을 보상 증가한다")
    void issue_fail_whenDataIntegrityViolationOccurs() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(setOperations.isMember(issuedUsersKey, String.valueOf(userId)))
            .thenReturn(false);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        )).thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        // when & then
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(valueOperations).increment(stockKey);
    }

    @Test
    @DisplayName("issue 실패 - 발급 수량 증가 실패 시 쿠폰이 ACTIVE이면 수량 소진 예외가 발생하고 Redis 수량을 보상 증가한다")
    void issue_fail_whenIncreaseIssuedQuantityReturnsZeroAndCouponIsActive() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";

        User user = mock(User.class);
        Coupon coupon = couponForIssue();
        UserCoupon savedUserCoupon = mock(UserCoupon.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(setOperations.isMember(issuedUsersKey, String.valueOf(userId)))
            .thenReturn(false);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        )).thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenReturn(savedUserCoupon);

        when(couponRepository.increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        )).thenReturn(0);

        when(coupon.getStatus())
            .thenReturn(CouponStatus.ACTIVE);

        // when & then
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        ))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.COUPON_001);

        verify(couponRepository).increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        );
        verify(valueOperations).increment(stockKey);
        verify(setOperations, never()).remove(anyString(), anyString());
    }

    @Test
    @DisplayName("issue 실패 - Redis Set add 실패 시 Redis 수량만 보상하고 발급 사용자 Set은 제거하지 않는다")
    void issue_fail_whenRedisSetAddFails_thenCompensateOnlyRedisStock() {
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

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(setOperations.isMember(issuedUsersKey, userIdValue))
            .thenReturn(false);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        )).thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenReturn(savedUserCoupon);
        when(couponRepository.increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        )).thenReturn(1);

        when(setOperations.add(issuedUsersKey, userIdValue))
            .thenThrow(new RuntimeException("Redis Set add failed"));

        // when & then
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(RuntimeException.class);

        verify(valueOperations).increment(stockKey);
        verify(setOperations, never()).remove(
            issuedUsersKey,
            userIdValue
        );
    }

    @Test
    @DisplayName("issue 실패 - 발급 수량 증가 시점에 쿠폰이 비활성화되면 비활성 쿠폰 예외가 발생하고 Redis 수량을 보상 증가한다")
    void issue_fail_whenCouponBecomesInactiveBeforeIncreaseIssuedQuantity() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";

        User user = mock(User.class);
        Coupon issuableCoupon = couponForIssue();
        Coupon inactiveCoupon = mock(Coupon.class);
        UserCoupon savedUserCoupon = mock(UserCoupon.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        // 첫 번째 조회: validateIssuable() 통과용
        // 두 번째 조회: updatedCount == 0 이후 실패 원인 분기용
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(issuableCoupon))
            .thenReturn(Optional.of(inactiveCoupon));

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(setOperations.isMember(issuedUsersKey, String.valueOf(userId)))
            .thenReturn(false);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        )).thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenReturn(savedUserCoupon);

        when(couponRepository.increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        )).thenReturn(0);

        when(inactiveCoupon.getStatus())
            .thenReturn(CouponStatus.INACTIVE);

        // when & then
        assertThatThrownBy(() -> decrCouponIssueStrategy.issue(
            userId,
            couponId
        ))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.COUPON_010);

        verify(couponRepository).increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        );

        // Redis DECR 성공 이후 DB 처리 실패이므로 수량 보상 필요
        verify(valueOperations).increment(stockKey);

        // issued-users Set add 전에 실패했으므로 제거 보상은 수행되지 않아야 함
        verify(setOperations, never()).remove(anyString(), anyString());
    }

    @Test
    @DisplayName("issue 보상 - 트랜잭션 롤백 콜백 실행 시 Redis 수량과 발급 사용자 Set을 보상한다")
    void issue_fail_whenTransactionRollback_thenCompensateRedisStockAndIssuedUsersSet() {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String stockKey = "coupon:stock:10";
        String issuedUsersKey = "coupon:issued-users:10";
        String userIdValue = String.valueOf(userId);

        User user = mock(User.class);
        Coupon coupon = couponForIssueResponse(couponId);
        UserCoupon savedUserCoupon = issuedUserCoupon(100L, coupon);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(redisTemplate.opsForSet())
            .thenReturn(setOperations);
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(setOperations.isMember(issuedUsersKey, userIdValue))
            .thenReturn(false);
        when(valueOperations.setIfAbsent(
            eq(stockKey),
            anyString(),
            any(Duration.class)
        )).thenReturn(true);
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            eq(List.of(stockKey))
        )).thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenReturn(savedUserCoupon);
        when(couponRepository.increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        )).thenReturn(1);

        when(setOperations.add(issuedUsersKey, userIdValue))
            .thenReturn(1L);
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            anyList(),
            anyString()
        )).thenReturn(1L);

        // when
        decrCouponIssueStrategy.issue(userId, couponId);

        TransactionSynchronizationManager.getSynchronizations()
            .forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            );

        // then
        verify(valueOperations).increment(stockKey);
        verify(setOperations).remove(issuedUsersKey, userIdValue);
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
