package com.sparta.one_stop.domain.point.entity;

import com.sparta.one_stop.domain.point.util.PointIntegrityHasher;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointTest {

    @BeforeAll
    static void initHasher() {
        PointIntegrityHasher hasher = new PointIntegrityHasher(
            "test-secret-key-for-unit-test-32bytes!!"
        );

        setStaticField(
            PointIntegrityHasher.class,
            "INSTANCE",
            hasher
        );
    }

    private static User mockUser(Long userId) {
        User user = mock(User.class);

        when(user.getId()).thenReturn(userId);

        return user;
    }

    private static Point createPointWithBalance(
        Long userId,
        int balance
    ) {
        User user = mockUser(userId);
        Point point = Point.createInitial(user);

        if (balance > 0) {
            point.increaseBalance(balance);
        }

        return point;
    }

    private static void setStaticField(
        Class<?> clazz,
        String fieldName,
        Object value
    ) {
        try {
            Field field = clazz.getDeclaredField(fieldName);

            field.setAccessible(true);
            field.set(
                null,
                value
            );
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("createInitial 성공 - balance 0, version 0으로 초기화된다")
    void createInitial_success() {
        // given
        User user = mockUser(1L);

        // when
        Point point = Point.createInitial(user);

        // then
        assertThat(point.getUser()).isSameAs(user);
        assertThat(point.getBalance()).isEqualTo(0);
        assertThat(point.getVersion()).isEqualTo(0);
        assertThat(point.getIntegrityHash()).isNotNull();
        assertThat(point.getIntegrityHash()).hasSize(64);
    }

    @Test
    @DisplayName("createInitial 실패 - 사용자가 null이면 예외가 발생한다")
    void createInitial_fail_whenUserIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> Point.createInitial(null)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_020);
    }

    @Test
    @DisplayName("createInitial 실패 - 사용자 ID가 null이면 예외가 발생한다")
    void createInitial_fail_whenUserIdIsNull() {
        // given
        User user = mockUser(null);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> Point.createInitial(user)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_021);
    }

    @Test
    @DisplayName("increaseBalance 성공 - balance가 증가한다")
    void increaseBalance_success() {
        // given
        Point point = createPointWithBalance(
            1L,
            1000
        );

        // when
        point.increaseBalance(500);

        // then
        assertThat(point.getBalance()).isEqualTo(1500);
        assertThat(point.getIntegrityHash()).isNotNull();
    }

    @Test
    @DisplayName("increaseBalance 실패 - amount가 null이면 예외가 발생한다")
    void increaseBalance_fail_whenAmountIsNull() {
        // given
        Point point = createPointWithBalance(
            1L,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> point.increaseBalance(null)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
        assertThat(point.getBalance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("increaseBalance 실패 - amount가 0이면 예외가 발생한다")
    void increaseBalance_fail_whenAmountIsZero() {
        // given
        Point point = createPointWithBalance(
            1L,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> point.increaseBalance(0)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
        assertThat(point.getBalance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("increaseBalance 실패 - amount가 음수이면 예외가 발생한다")
    void increaseBalance_fail_whenAmountIsNegative() {
        // given
        Point point = createPointWithBalance(
            1L,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> point.increaseBalance(-1)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
        assertThat(point.getBalance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("decreaseBalance 성공 - balance가 감소한다")
    void decreaseBalance_success() {
        // given
        Point point = createPointWithBalance(
            1L,
            1000
        );

        // when
        point.decreaseBalance(500);

        // then
        assertThat(point.getBalance()).isEqualTo(500);
        assertThat(point.getIntegrityHash()).isNotNull();
    }

    @Test
    @DisplayName("decreaseBalance 실패 - amount가 null이면 예외가 발생한다")
    void decreaseBalance_fail_whenAmountIsNull() {
        // given
        Point point = createPointWithBalance(
            1L,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> point.decreaseBalance(null)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
        assertThat(point.getBalance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("decreaseBalance 실패 - amount가 0이면 예외가 발생한다")
    void decreaseBalance_fail_whenAmountIsZero() {
        // given
        Point point = createPointWithBalance(
            1L,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> point.decreaseBalance(0)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
        assertThat(point.getBalance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("decreaseBalance 실패 - amount가 음수이면 예외가 발생한다")
    void decreaseBalance_fail_whenAmountIsNegative() {
        // given
        Point point = createPointWithBalance(
            1L,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> point.decreaseBalance(-1)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
        assertThat(point.getBalance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("decreaseBalance 실패 - balance보다 큰 금액이면 예외가 발생한다")
    void decreaseBalance_fail_whenAmountExceedsBalance() {
        // given
        Point point = createPointWithBalance(
            1L,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> point.decreaseBalance(1001)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_002);
        assertThat(point.getBalance()).isEqualTo(1000);
    }

    @Test
    @DisplayName("verifyIntegrity 성공 - 현재 balance, version, hash가 일치하면 예외가 발생하지 않는다")
    void verifyIntegrity_success() {
        // given
        Point point = Point.createInitial(mockUser(1L));

        // when
        point.verifyIntegrity();

        // then
        assertThat(point.getIntegrityHash()).isNotNull();
    }

}
