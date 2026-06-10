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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LockCouponIssueStrategyTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private LockCouponIssueStrategy lockCouponIssueStrategy;

    @Test
    @DisplayName("supports 성공 - strategyType이 lock이면 true를 반환한다")
    void supports_success_whenStrategyTypeIsLock() {
        // when
        boolean result = lockCouponIssueStrategy.supports("lock");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("supports 성공 - strategyType이 대소문자가 달라도 lock이면 true를 반환한다")
    void supports_success_whenStrategyTypeIsLockIgnoreCase() {
        // when
        boolean result = lockCouponIssueStrategy.supports("LOCK");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("supports 성공 - strategyType이 lock이 아니면 false를 반환한다")
    void supports_success_whenStrategyTypeIsNotLock() {
        // when
        boolean result = lockCouponIssueStrategy.supports("decr");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("supports 성공 - strategyType이 null이면 false를 반환한다")
    void supports_success_whenStrategyTypeIsNull() {
        // when
        boolean result = lockCouponIssueStrategy.supports(null);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("issue 성공 - Redis Lock 획득 후 UserCoupon을 저장하고 발급 수량을 증가시킨다")
    void issue_success() throws InterruptedException {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String lockKey = "lock:coupon:issue:10";

        User user = mock(User.class);
        Coupon coupon = couponForIssueResponse(couponId);
        UserCoupon savedUserCoupon = issuedUserCoupon(
            100L,
            coupon
        );

        when(redissonClient.getLock(lockKey))
            .thenReturn(lock);
        when(lock.tryLock(
            anyLong(),
            anyLong(),
            eq(TimeUnit.SECONDS)
        )).thenReturn(true);
        when(lock.isHeldByCurrentThread())
            .thenReturn(true);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenReturn(savedUserCoupon);
        when(couponRepository.increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        )).thenReturn(1);

        // when
        IssueCouponResponse result = lockCouponIssueStrategy.issue(
            userId,
            couponId
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.userCouponId()).isEqualTo(100L);
        assertThat(result.couponId()).isEqualTo(couponId);
        assertThat(result.couponName()).isEqualTo("테스트 쿠폰");
        assertThat(result.status()).isEqualTo(UserCouponStatus.AVAILABLE);

        InOrder inOrder = inOrder(
            redissonClient,
            lock,
            userCouponRepository,
            couponRepository
        );

        inOrder.verify(redissonClient).getLock(lockKey);
        inOrder.verify(lock).tryLock(
            anyLong(),
            anyLong(),
            eq(TimeUnit.SECONDS)
        );
        inOrder.verify(userCouponRepository).saveAndFlush(any(UserCoupon.class));
        inOrder.verify(couponRepository).increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        );
        inOrder.verify(lock).unlock();
    }

    @Test
    @DisplayName("issue 실패 - userId가 null이면 인증 예외가 발생하고 Lock을 획득하지 않는다")
    void issue_fail_whenUserIdIsNull() {
        // given
        Long userId = null;
        Long couponId = 10L;

        // when & then
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redissonClient, never()).getLock(anyString());
        verify(userRepository, never()).findById(any());
        verify(couponRepository, never()).findById(any());
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - couponId가 null이면 쿠폰 조회 예외가 발생하고 Lock을 획득하지 않는다")
    void issue_fail_whenCouponIdIsNull() {
        // given
        Long userId = 1L;
        Long couponId = null;

        // when & then
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(redissonClient, never()).getLock(anyString());
        verify(userRepository, never()).findById(any());
        verify(couponRepository, never()).findById(any());
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
    }

    @Test
    @DisplayName("issue 실패 - Redis Lock 획득에 실패하면 예외가 발생하고 발급 처리를 하지 않는다")
    void issue_fail_whenLockNotAcquired() throws InterruptedException {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String lockKey = "lock:coupon:issue:10";

        when(redissonClient.getLock(lockKey))
            .thenReturn(lock);
        when(lock.tryLock(
            anyLong(),
            anyLong(),
            eq(TimeUnit.SECONDS)
        )).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userRepository, never()).findById(any());
        verify(couponRepository, never()).findById(any());
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("issue 실패 - Lock 획득 대기 중 인터럽트가 발생하면 인터럽트 상태를 복구하고 예외가 발생한다")
    void issue_fail_whenInterruptedDuringLockWait() throws InterruptedException {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        String lockKey = "lock:coupon:issue:10";

        when(redissonClient.getLock(lockKey))
            .thenReturn(lock);
        when(lock.tryLock(
            anyLong(),
            anyLong(),
            eq(TimeUnit.SECONDS)
        )).thenThrow(new InterruptedException());

        // when & then
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        // 다음 테스트에 영향 없도록 인터럽트 상태 제거
        Thread.interrupted();

        verify(userRepository, never()).findById(any());
        verify(couponRepository, never()).findById(any());
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("issue 실패 - 존재하지 않는 사용자면 예외가 발생하고 Lock을 해제한다")
    void issue_fail_whenUserNotFound() throws InterruptedException {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        prepareLockAcquired(couponId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userRepository).findById(userId);
        verify(couponRepository, never()).findById(any());
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("issue 실패 - 존재하지 않는 쿠폰이면 예외가 발생하고 Lock을 해제한다")
    void issue_fail_whenCouponNotFound() throws InterruptedException {
        // given
        Long userId = 1L;
        Long couponId = 10L;
        User user = mock(User.class);

        prepareLockAcquired(couponId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userRepository).findById(userId);
        verify(couponRepository).findById(couponId);
        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("issue 실패 - 쿠폰이 비활성 또는 만료 상태이면 예외가 발생하고 Lock을 해제한다")
    void issue_fail_whenCouponIsNotIssuable() throws InterruptedException {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);

        prepareLockAcquired(couponId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));

        doThrow(CustomException.class)
            .when(coupon)
            .validateIssuable(any(LocalDateTime.class));

        // when & then
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("issue 실패 - DB 중복 발급이면 중복 발급 예외가 발생하고 Lock을 해제한다")
    void issue_fail_whenAlreadyIssuedInDb() throws InterruptedException {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        prepareLockAcquired(couponId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));
        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(userCouponRepository, never()).saveAndFlush(any(UserCoupon.class));
        verify(couponRepository, never()).increaseIssuedQuantity(
            anyLong(),
            any(CouponStatus.class)
        );
        verify(lock).unlock();
    }

    @Test
    @DisplayName("issue 실패 - UserCoupon 저장 중 Unique 제약 위반이 발생하면 중복 발급 예외가 발생하고 Lock을 해제한다")
    void issue_fail_whenDataIntegrityViolationOccurs() throws InterruptedException {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        prepareLockAcquired(couponId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));
        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenReturn(false);
        when(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        // when & then
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(couponRepository, never()).increaseIssuedQuantity(
            anyLong(),
            any(CouponStatus.class)
        );
        verify(lock).unlock();
    }

    @Test
    @DisplayName("issue 실패 - issuedQuantity 증가 실패 시 쿠폰이 ACTIVE이면 쿠폰 소진 예외가 발생하고 Lock을 해제한다")
    void issue_fail_whenIncreaseIssuedQuantityFails() throws InterruptedException {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        User user = mock(User.class);
        Coupon coupon = couponForIssue();
        UserCoupon savedUserCoupon = mock(UserCoupon.class);

        prepareLockAcquired(couponId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));
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
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(
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
        verify(lock).unlock();
    }

    @Test
    @DisplayName("issue 실패 - 예상치 못한 RuntimeException이 발생해도 Lock을 해제한다")
    void issue_fail_whenRuntimeExceptionOccurs_thenUnlock() throws InterruptedException {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        User user = mock(User.class);
        Coupon coupon = couponForIssue();

        prepareLockAcquired(couponId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(coupon));
        when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId))
            .thenThrow(new RuntimeException("DB read failed"));

        // when & then
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(RuntimeException.class);

        verify(lock).unlock();
    }

    @Test
    @DisplayName("issue 실패 - 현재 스레드가 Lock을 보유하지 않으면 unlock을 호출하지 않는다")
    void issue_fail_whenCurrentThreadDoesNotHoldLock_thenDoNotUnlock() throws InterruptedException {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        prepareLockAcquiredButNotHeldByCurrentThread(couponId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(
            userId,
            couponId
        )).isInstanceOf(CustomException.class);

        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("issue 실패 - 발급 수량 증가 시점에 쿠폰이 비활성화되면 비활성 쿠폰 예외가 발생하고 Lock을 해제한다")
    void issue_fail_whenCouponBecomesInactiveBeforeIncreaseIssuedQuantity() throws InterruptedException {
        // given
        Long userId = 1L;
        Long couponId = 10L;

        User user = mock(User.class);
        Coupon issuableCoupon = couponForIssue();
        Coupon inactiveCoupon = mock(Coupon.class);
        UserCoupon savedUserCoupon = mock(UserCoupon.class);

        prepareLockAcquired(couponId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(couponRepository.findById(couponId))
            .thenReturn(Optional.of(issuableCoupon))
            .thenReturn(Optional.of(inactiveCoupon));

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
        assertThatThrownBy(() -> lockCouponIssueStrategy.issue(userId, couponId))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.COUPON_010);

        verify(couponRepository).increaseIssuedQuantity(
            couponId,
            CouponStatus.ACTIVE
        );
        verify(lock).unlock();
    }

    private void prepareLockAcquired(Long couponId) throws InterruptedException {
        String lockKey = "lock:coupon:issue:" + couponId;

        when(redissonClient.getLock(lockKey))
            .thenReturn(lock);
        when(lock.tryLock(
            anyLong(),
            anyLong(),
            eq(TimeUnit.SECONDS)
        )).thenReturn(true);
        when(lock.isHeldByCurrentThread())
            .thenReturn(true);
    }

    private void prepareLockAcquiredButNotHeldByCurrentThread(Long couponId) throws InterruptedException {
        String lockKey = "lock:coupon:issue:" + couponId;

        when(redissonClient.getLock(lockKey))
            .thenReturn(lock);
        when(lock.tryLock(
            anyLong(),
            anyLong(),
            eq(TimeUnit.SECONDS)
        )).thenReturn(true);
        when(lock.isHeldByCurrentThread())
            .thenReturn(false);
    }

    private Coupon couponForIssue() {
        Coupon coupon = mock(Coupon.class);
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
