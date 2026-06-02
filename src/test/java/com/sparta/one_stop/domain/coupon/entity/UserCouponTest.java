package com.sparta.one_stop.domain.coupon.entity;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserCouponTest {

    @Test
    @DisplayName("UserCoupon 생성 성공 - 사용자와 쿠폰이 있으면 AVAILABLE 상태로 생성된다")
    void createUserCoupon_success() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);

        // when
        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        // then
        assertThat(userCoupon.getUser()).isEqualTo(user);
        assertThat(userCoupon.getCoupon()).isEqualTo(coupon);
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        assertThat(userCoupon.getUsedOrder()).isNull();
        assertThat(userCoupon.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("UserCoupon 생성 실패 - 사용자가 없으면 예외가 발생한다")
    void createUserCoupon_fail_whenUserIsNull() {
        // given
        Coupon coupon = mock(Coupon.class);

        // when & then
        assertThatThrownBy(() -> new UserCoupon(
            null,
            coupon
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("UserCoupon 생성 실패 - 쿠폰이 없으면 예외가 발생한다")
    void createUserCoupon_fail_whenCouponIsNull() {
        // given
        User user = mock(User.class);

        // when & then
        assertThatThrownBy(() -> new UserCoupon(
            user,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("use 성공 - AVAILABLE 쿠폰을 USED 상태로 변경하고 사용 주문과 사용 일시를 저장한다")
    void use_success() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        Order order = mock(Order.class);
        LocalDateTime usedAt = LocalDateTime.now();

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        when(coupon.isExpired(usedAt))
            .thenReturn(false);

        // when
        userCoupon.use(
            order,
            usedAt
        );

        // then
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
        assertThat(userCoupon.getUsedOrder()).isEqualTo(order);
        assertThat(userCoupon.getUsedAt()).isEqualTo(usedAt);

        verify(coupon).isExpired(usedAt);
    }

    @Test
    @DisplayName("use 실패 - 주문 정보가 없으면 예외가 발생한다")
    void use_fail_whenOrderIsNull() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        LocalDateTime usedAt = LocalDateTime.now();

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        // when & then
        assertThatThrownBy(() -> userCoupon.use(
            null,
            usedAt
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("use 실패 - 사용 일시가 없으면 예외가 발생한다")
    void use_fail_whenUsedAtIsNull() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        Order order = mock(Order.class);

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        // when & then
        assertThatThrownBy(() -> userCoupon.use(
            order,
            null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("use 실패 - 이미 사용된 쿠폰이면 예외가 발생한다")
    void use_fail_whenAlreadyUsed() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        Order firstOrder = mock(Order.class);
        Order secondOrder = mock(Order.class);
        LocalDateTime firstUsedAt = LocalDateTime.now();
        LocalDateTime secondUsedAt = firstUsedAt.plusMinutes(1);

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        when(coupon.isExpired(firstUsedAt))
            .thenReturn(false);

        userCoupon.use(
            firstOrder,
            firstUsedAt
        );

        // when & then
        assertThatThrownBy(() -> userCoupon.use(
            secondOrder,
            secondUsedAt
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("use 실패 - 만료된 쿠폰이면 예외가 발생한다")
    void use_fail_whenCouponIsExpired() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        Order order = mock(Order.class);
        LocalDateTime usedAt = LocalDateTime.now();

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        when(coupon.isExpired(usedAt))
            .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userCoupon.use(
            order,
            usedAt
        )).isInstanceOf(IllegalStateException.class);

        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        assertThat(userCoupon.getUsedOrder()).isNull();
        assertThat(userCoupon.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("restore 성공 - 만료 전 USED 쿠폰은 AVAILABLE 상태로 복구된다")
    void restore_success_whenCouponIsNotExpired() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        Order order = mock(Order.class);
        LocalDateTime usedAt = LocalDateTime.now();
        LocalDateTime now = usedAt.plusDays(1);

        UserCoupon userCoupon = usedUserCoupon(
            user,
            coupon,
            order,
            usedAt
        );

        when(coupon.isExpired(now))
            .thenReturn(false);

        // when
        userCoupon.restore(now);

        // then
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
        assertThat(userCoupon.getUsedOrder()).isNull();
        assertThat(userCoupon.getUsedAt()).isNull();

        verify(coupon).isExpired(now);
    }

    @Test
    @DisplayName("restore 성공 - 만료 후 USED 쿠폰은 EXPIRED 상태로 복구된다")
    void restore_success_whenCouponIsExpired() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        Order order = mock(Order.class);
        LocalDateTime usedAt = LocalDateTime.now();
        LocalDateTime now = usedAt.plusDays(30);

        UserCoupon userCoupon = usedUserCoupon(
            user,
            coupon,
            order,
            usedAt
        );

        when(coupon.isExpired(now))
            .thenReturn(true);

        // when
        userCoupon.restore(now);

        // then
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.EXPIRED);
        assertThat(userCoupon.getUsedOrder()).isNull();
        assertThat(userCoupon.getUsedAt()).isNull();

        verify(coupon).isExpired(now);
    }

    @Test
    @DisplayName("restore 실패 - 현재 시각이 없으면 예외가 발생한다")
    void restore_fail_whenNowIsNull() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        Order order = mock(Order.class);
        LocalDateTime usedAt = LocalDateTime.now();

        UserCoupon userCoupon = usedUserCoupon(
            user,
            coupon,
            order,
            usedAt
        );

        // when & then
        assertThatThrownBy(() -> userCoupon.restore(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("restore 실패 - USED 상태가 아니면 예외가 발생한다")
    void restore_fail_whenStatusIsNotUsed() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        LocalDateTime now = LocalDateTime.now();

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        // when & then
        assertThatThrownBy(() -> userCoupon.restore(now))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("expire 성공 - 만료된 AVAILABLE 쿠폰은 EXPIRED 상태로 변경된다")
    void expire_success() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        LocalDateTime now = LocalDateTime.now();

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        when(coupon.isExpired(now))
            .thenReturn(true);

        // when
        userCoupon.expire(now);

        // then
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.EXPIRED);

        verify(coupon).isExpired(now);
    }

    @Test
    @DisplayName("expire 성공 - 이미 EXPIRED 상태이면 아무 처리도 하지 않는다")
    void expire_success_whenAlreadyExpired() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        LocalDateTime now = LocalDateTime.now();

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        when(coupon.isExpired(now))
            .thenReturn(true);

        userCoupon.expire(now);

        // when
        userCoupon.expire(now.plusDays(1));

        // then
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.EXPIRED);
    }

    @Test
    @DisplayName("expire 실패 - 현재 시각이 없으면 예외가 발생한다")
    void expire_fail_whenNowIsNull() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        // when & then
        assertThatThrownBy(() -> userCoupon.expire(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("expire 실패 - 이미 사용된 쿠폰은 만료 처리할 수 없다")
    void expire_fail_whenAlreadyUsed() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        Order order = mock(Order.class);
        LocalDateTime usedAt = LocalDateTime.now();
        LocalDateTime now = usedAt.plusDays(1);

        UserCoupon userCoupon = usedUserCoupon(
            user,
            coupon,
            order,
            usedAt
        );

        // when & then
        assertThatThrownBy(() -> userCoupon.expire(now))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("expire 실패 - 아직 만료되지 않은 쿠폰이면 예외가 발생한다")
    void expire_fail_whenCouponIsNotExpired() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        LocalDateTime now = LocalDateTime.now();

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        when(coupon.isExpired(now))
            .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> userCoupon.expire(now))
            .isInstanceOf(IllegalStateException.class);

        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
    }

    @Test
    @DisplayName("validateUsable 성공 - 본인 쿠폰이고 AVAILABLE 상태이며 사용 가능 기간이면 통과한다")
    void validateUsable_success() {
        // given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        when(user.getId())
            .thenReturn(userId);
        when(coupon.isInPeriod(now))
            .thenReturn(true);

        // when & then
        assertThatNoException().isThrownBy(() -> userCoupon.validateUsable(
            userId,
            now
        ));

        verify(user).getId();
        verify(coupon).isInPeriod(now);
    }

    @Test
    @DisplayName("validateUsable 실패 - 사용자 ID가 없으면 예외가 발생한다")
    void validateUsable_fail_whenUserIdIsNull() {
        // given
        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        LocalDateTime now = LocalDateTime.now();

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        // when & then
        assertThatThrownBy(() -> userCoupon.validateUsable(
            null,
            now
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateUsable 실패 - 본인 쿠폰이 아니면 예외가 발생한다")
    void validateUsable_fail_whenNotOwner() {
        // given
        Long ownerId = 1L;
        Long otherUserId = 2L;
        LocalDateTime now = LocalDateTime.now();

        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        when(user.getId())
            .thenReturn(ownerId);

        // when & then
        assertThatThrownBy(() -> userCoupon.validateUsable(
            otherUserId,
            now
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("validateUsable 실패 - AVAILABLE 상태가 아니면 예외가 발생한다")
    void validateUsable_fail_whenStatusIsNotAvailable() {
        // given
        Long userId = 1L;
        LocalDateTime usedAt = LocalDateTime.now();
        LocalDateTime now = usedAt.plusDays(1);

        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);
        Order order = mock(Order.class);

        UserCoupon userCoupon = usedUserCoupon(
            user,
            coupon,
            order,
            usedAt
        );

        when(user.getId())
            .thenReturn(userId);

        // when & then
        assertThatThrownBy(() -> userCoupon.validateUsable(
            userId,
            now
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("validateUsable 실패 - 쿠폰 사용 가능 기간이 아니면 예외가 발생한다")
    void validateUsable_fail_whenCouponIsNotInPeriod() {
        // given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        User user = mock(User.class);
        Coupon coupon = mock(Coupon.class);

        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        when(user.getId())
            .thenReturn(userId);
        when(coupon.isInPeriod(now))
            .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> userCoupon.validateUsable(
            userId,
            now
        )).isInstanceOf(IllegalStateException.class);
    }

    private UserCoupon usedUserCoupon(
        User user,
        Coupon coupon,
        Order order,
        LocalDateTime usedAt
    ) {
        UserCoupon userCoupon = new UserCoupon(
            user,
            coupon
        );

        when(coupon.isExpired(usedAt))
            .thenReturn(false);

        userCoupon.use(
            order,
            usedAt
        );

        return userCoupon;
    }

}
