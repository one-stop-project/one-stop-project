package com.sparta.one_stop.domain.coupon.service;

import com.sparta.one_stop.domain.coupon.dto.CouponRestoreResult;
import com.sparta.one_stop.domain.coupon.dto.response.IssueCouponResponse;
import com.sparta.one_stop.domain.coupon.entity.Coupon;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.coupon.repository.CouponRepository;
import com.sparta.one_stop.domain.coupon.repository.UserCouponRepository;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.exception.CustomException;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponCommandServiceTest {

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
    private CouponCommandService couponCommandService;

    @Test
    @DisplayName("issueCoupon 성공 - Redis 수량 차감 후 UserCoupon을 저장하고 발급 사용자 Set에 기록한다")
    void issueCoupon_success() {
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
        when(valueOperations.decrement(stockKey))
            .thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenReturn(savedUserCoupon);
        when(couponRepository.increaseIssuedQuantity(couponId))
            .thenReturn(1);

        when(setOperations.add(issuedUsersKey, String.valueOf(userId)))
            .thenReturn(1L);
        when(redisTemplate.execute(
            any(DefaultRedisScript.class),
            anyList(),
            anyString()
        )).thenReturn(1L);

        // when
        IssueCouponResponse result = couponCommandService.issueCoupon(
            userId,
            couponId
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.userCouponId()).isEqualTo(100L);
        assertThat(result.couponId()).isEqualTo(couponId);
        assertThat(result.couponName()).isEqualTo("테스트 쿠폰");
        assertThat(result.status()).isEqualTo(UserCouponStatus.AVAILABLE);

        verify(valueOperations).decrement(stockKey);
        verify(userCouponRepository).saveAndFlush(any(UserCoupon.class));
        verify(couponRepository).increaseIssuedQuantity(couponId);
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
    @DisplayName("issueCoupon 실패 - 존재하지 않는 사용자면 예외 발생")
    void issueCoupon_fail_whenUserNotFound() {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        when(userRepository.findById(userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponCommandService.issueCoupon(
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
    @DisplayName("issueCoupon 실패 - 존재하지 않는 쿠폰이면 예외 발생")
    void issueCoupon_fail_whenCouponNotFound() {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        User user = mock(User.class);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponCommandService.issueCoupon(
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
    @DisplayName("issueCoupon 실패 - 쿠폰이 비활성 또는 만료 상태이면 발급 가능 검증에서 예외가 발생한다")
    void issueCoupon_fail_whenCouponIsNotIssuable() {
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
        assertThatThrownBy(() -> couponCommandService.issueCoupon(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).opsForSet();
    }

    @Test
    @DisplayName("issueCoupon 실패 - Redis Set에 이미 발급 사용자로 기록되어 있으면 중복 발급 예외가 발생한다")
    void issueCoupon_fail_whenAlreadyIssuedInRedis() {
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
        assertThatThrownBy(() -> couponCommandService.issueCoupon(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redisTemplate, never()).opsForValue();
        verify(valueOperations, never()).decrement(anyString());
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issueCoupon 실패 - Redis DECR 결과가 음수이면 수량 소진 예외가 발생하고 Redis 수량을 보상 증가한다")
    void issueCoupon_fail_whenRedisStockIsSoldOut() {
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
        when(valueOperations.decrement(stockKey))
            .thenReturn(-1L);

        // when & then
        assertThatThrownBy(() -> couponCommandService.issueCoupon(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(valueOperations).increment(stockKey);
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
        verify(setOperations, never()).add(anyString(), anyString());
    }

    @Test
    @DisplayName("issueCoupon 실패 - DB 중복 발급이면 Redis 수량을 보상 증가하고 중복 발급 예외가 발생한다")
    void issueCoupon_fail_whenAlreadyIssuedInDb() {
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
        when(valueOperations.decrement(stockKey))
            .thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> couponCommandService.issueCoupon(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(valueOperations).increment(stockKey);
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issueCoupon 실패 - UserCoupon 저장 중 Unique 제약 위반이 발생하면 Redis 수량을 보상 증가한다")
    void issueCoupon_fail_whenDataIntegrityViolationOccurs() {
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
        when(valueOperations.decrement(stockKey))
            .thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        // when & then
        assertThatThrownBy(() -> couponCommandService.issueCoupon(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(valueOperations).increment(stockKey);
    }

    @Test
    @DisplayName("issueCoupon 실패 - issuedQuantity 증가 실패 시 Redis 수량을 보상 증가한다")
    void issueCoupon_fail_whenIncreaseIssuedQuantityFails() {
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
        when(valueOperations.decrement(stockKey))
            .thenReturn(99L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenReturn(savedUserCoupon);
        when(couponRepository.increaseIssuedQuantity(couponId))
            .thenReturn(0);

        // when & then
        assertThatThrownBy(() -> couponCommandService.issueCoupon(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(valueOperations).increment(stockKey);
    }

    @Test
    @DisplayName("useCouponByOrder 성공 - 주문에 적용된 쿠폰을 USED 처리한다")
    void useCouponByOrder_success() {
        // given
        Long userId = 1L;

        Order order = mock(Order.class);
        User user = mock(User.class);
        UserCoupon userCoupon = mock(UserCoupon.class);

        when(order.getUserCoupon())
            .thenReturn(userCoupon);
        when(order.getUser())
            .thenReturn(user);
        when(user.getId())
            .thenReturn(userId);

        // when
        couponCommandService.useCouponByOrder(order);

        // then
        verify(userCoupon).validateUsable(
            eq(userId),
            any(LocalDateTime.class)
        );
        verify(userCoupon).use(
            eq(order),
            any(LocalDateTime.class)
        );
    }

    @Test
    @DisplayName("useCouponByOrder 성공 - 주문에 쿠폰이 없으면 아무 처리도 하지 않는다")
    void useCouponByOrder_success_whenOrderHasNoCoupon() {
        // given
        Order order = mock(Order.class);

        when(order.getUserCoupon())
            .thenReturn(null);

        // when
        couponCommandService.useCouponByOrder(order);

        // then
        verify(order).getUserCoupon();
        verify(order, never()).getUser();
    }

    @Test
    @DisplayName("restoreCouponByOrder 성공 - 결제 완료 주문의 사용 쿠폰을 복구하고 결과를 반환한다")
    void restoreCouponByOrder_success() {
        // given
        Order order = mock(Order.class);
        UserCoupon userCoupon = mock(UserCoupon.class);
        Coupon coupon = mock(Coupon.class);

        when(order.getUserCoupon())
            .thenReturn(userCoupon);
        when(order.getStatus())
            .thenReturn(OrderStatus.PAID);

        when(userCoupon.getId())
            .thenReturn(100L);
        when(userCoupon.getCoupon())
            .thenReturn(coupon);
        when(coupon.getName())
            .thenReturn("테스트 쿠폰");

        AtomicReference<UserCouponStatus> statusRef =
            new AtomicReference<>(UserCouponStatus.USED);

        when(userCoupon.getStatus())
            .thenAnswer(invocation -> statusRef.get());

        doAnswer(invocation -> {
            statusRef.set(UserCouponStatus.AVAILABLE);
            return null;
        }).when(userCoupon).restore(any(LocalDateTime.class));

        // when
        CouponRestoreResult result = couponCommandService.restoreCouponByOrder(order);

        // then
        assertThat(result).isNotNull();
        assertThat(result.userCouponId()).isEqualTo(100L);
        assertThat(result.couponName()).isEqualTo("테스트 쿠폰");
        assertThat(result.status()).isEqualTo(UserCouponStatus.AVAILABLE);

        verify(userCoupon).restore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("restoreCouponByOrder 성공 - 결제 전 주문이면 쿠폰 복구를 하지 않는다")
    void restoreCouponByOrder_success_whenPendingPaymentOrder() {
        // given
        Order order = mock(Order.class);
        UserCoupon userCoupon = mock(UserCoupon.class);

        when(order.getUserCoupon())
            .thenReturn(userCoupon);
        when(order.getStatus())
            .thenReturn(OrderStatus.PENDING_PAYMENT);

        // when
        CouponRestoreResult result = couponCommandService.restoreCouponByOrder(order);

        // then
        assertThat(result).isNull();
        verify(userCoupon, never()).restore(any(LocalDateTime.class));
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
