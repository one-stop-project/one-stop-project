package com.sparta.one_stop.domain.point.entity;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.point.util.PointIntegrityHasher;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointHistoryTest {

    private static final LocalDate EXPIRE_AT = LocalDate.of(2027, 6, 5);

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

    // ── charge ──

    private static User mockUser(Long userId) {
        User user = mock(User.class);

        when(user.getId()).thenReturn(userId);

        return user;
    }

    // ── earn ──

    private static Order mockOrder(Long orderId) {
        Order order = mock(Order.class);

        when(order.getId()).thenReturn(orderId);

        return order;
    }

    // ── use ──

    private static Point createPoint(Long userId) {
        return Point.createInitial(mockUser(userId));
    }

    // ── refund ──

    private static PointHistory chargeHistory(
        Point point,
        int amount
    ) {
        return PointHistory.charge(
            point,
            amount,
            "테스트 충전",
            EXPIRE_AT
        );
    }

    // ── expire ──

    private static PointHistory useHistory(
        Point point,
        int amount
    ) {
        return PointHistory.use(
            point,
            mockOrder(1L),
            amount,
            "테스트 사용"
        );
    }

    // ── 필수값 검증 ──

    private static PointHistory createPointHistory(
        Point point,
        User user,
        Order order,
        Integer amount,
        Integer remainingAmount,
        PointHistoryType type,
        String description,
        LocalDate expireAt
    ) {
        try {
            Constructor<PointHistory> constructor = PointHistory.class.getDeclaredConstructor(
                Point.class,
                User.class,
                Order.class,
                Integer.class,
                Integer.class,
                PointHistoryType.class,
                String.class,
                LocalDate.class
            );

            constructor.setAccessible(true);

            return constructor.newInstance(
                point,
                user,
                order,
                amount,
                remainingAmount,
                type,
                description,
                expireAt
            );
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();

            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
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
    @DisplayName("charge 성공 - CHARGE 이력이 생성되고 remainingAmount가 amount와 동일하다")
    void charge_success() {
        // given
        Point point = createPoint(1L);

        // when
        PointHistory history = PointHistory.charge(
            point,
            1000,
            "테스트 충전",
            EXPIRE_AT
        );

        // then
        assertThat(history.getPoint()).isSameAs(point);
        assertThat(history.getUser()).isSameAs(point.getUser());
        assertThat(history.getOrder()).isNull();
        assertThat(history.getAmount()).isEqualTo(1000);
        assertThat(history.getRemainingAmount()).isEqualTo(1000);
        assertThat(history.getType()).isEqualTo(PointHistoryType.CHARGE);
        assertThat(history.getDescription()).isEqualTo("테스트 충전");
        assertThat(history.getExpireAt()).isEqualTo(EXPIRE_AT);
    }

    @Test
    @DisplayName("earn 성공 - EARN 이력이 생성되고 remainingAmount가 amount와 동일하다")
    void earn_success() {
        // given
        Point point = createPoint(1L);
        Order order = mockOrder(1L);

        // when
        PointHistory history = PointHistory.earn(
            point,
            order,
            500,
            "배송 완료 적립",
            EXPIRE_AT
        );

        // then
        assertThat(history.getPoint()).isSameAs(point);
        assertThat(history.getUser()).isSameAs(point.getUser());
        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getAmount()).isEqualTo(500);
        assertThat(history.getRemainingAmount()).isEqualTo(500);
        assertThat(history.getType()).isEqualTo(PointHistoryType.EARN);
        assertThat(history.getDescription()).isEqualTo("배송 완료 적립");
        assertThat(history.getExpireAt()).isEqualTo(EXPIRE_AT);
    }

    @Test
    @DisplayName("use 성공 - USE 이력이 생성되고 amount는 음수, remainingAmount는 0이다")
    void use_success() {
        // given
        Point point = createPoint(1L);
        Order order = mockOrder(1L);

        // when
        PointHistory history = PointHistory.use(
            point,
            order,
            2000,
            "주문 결제 사용"
        );

        // then
        assertThat(history.getPoint()).isSameAs(point);
        assertThat(history.getUser()).isSameAs(point.getUser());
        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getAmount()).isEqualTo(-2000);
        assertThat(history.getRemainingAmount()).isEqualTo(0);
        assertThat(history.getType()).isEqualTo(PointHistoryType.USE);
        assertThat(history.getDescription()).isEqualTo("주문 결제 사용");
        assertThat(history.getExpireAt()).isNull();
    }

    @Test
    @DisplayName("refund 성공 - REFUND 이력이 생성되고 원래 만료일을 유지한다")
    void refund_success() {
        // given
        Point point = createPoint(1L);
        Order order = mockOrder(1L);
        LocalDate originalExpireAt = LocalDate.of(2027, 3, 15);

        // when
        PointHistory history = PointHistory.refund(
            point,
            order,
            1000,
            "주문 취소 복구",
            originalExpireAt
        );

        // then
        assertThat(history.getPoint()).isSameAs(point);
        assertThat(history.getUser()).isSameAs(point.getUser());
        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getAmount()).isEqualTo(1000);
        assertThat(history.getRemainingAmount()).isEqualTo(1000);
        assertThat(history.getType()).isEqualTo(PointHistoryType.REFUND);
        assertThat(history.getDescription()).isEqualTo("주문 취소 복구");
        assertThat(history.getExpireAt()).isEqualTo(originalExpireAt);
    }

    @Test
    @DisplayName("expire 성공 - EXPIRE 이력이 생성되고 amount는 음수, remainingAmount는 0이다")
    void expire_success() {
        // given
        Point point = createPoint(1L);

        // when
        PointHistory history = PointHistory.expire(
            point,
            500,
            "포인트 만료"
        );

        // then
        assertThat(history.getPoint()).isSameAs(point);
        assertThat(history.getUser()).isSameAs(point.getUser());
        assertThat(history.getOrder()).isNull();
        assertThat(history.getAmount()).isEqualTo(-500);
        assertThat(history.getRemainingAmount()).isEqualTo(0);
        assertThat(history.getType()).isEqualTo(PointHistoryType.EXPIRE);
        assertThat(history.getDescription()).isEqualTo("포인트 만료");
        assertThat(history.getExpireAt()).isNull();
    }

    // ── 유형별 정책 검증 ──

    @Test
    @DisplayName("생성 실패 - point가 null이면 예외가 발생한다")
    void charge_fail_whenPointIsNull() {
        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> PointHistory.charge(
                null,
                1000,
                "충전",
                EXPIRE_AT
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_022);
    }

    @Test
    @DisplayName("생성 실패 - point의 user가 null이면 예외가 발생한다")
    void create_fail_whenUserIsNull() {
        // given
        Point point = mock(Point.class);

        when(point.getUser())
            .thenReturn(null);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> PointHistory.charge(
                point,
                1000,
                "충전",
                EXPIRE_AT
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_023);
    }

    @Test
    @DisplayName("생성 실패 - amount가 null이면 예외가 발생한다")
    void charge_fail_whenAmountIsNull() {
        // given
        Point point = createPoint(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> PointHistory.charge(
                point,
                null,
                "충전",
                EXPIRE_AT
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_024);
    }

    @Test
    @DisplayName("생성 실패 - amount가 0이면 예외가 발생한다")
    void charge_fail_whenAmountIsZero() {
        // given
        Point point = createPoint(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> PointHistory.charge(
                point,
                0,
                "충전",
                EXPIRE_AT
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_024);
    }

    @Test
    @DisplayName("생성 실패 - remainingAmount가 null이면 예외가 발생한다")
    void create_fail_whenRemainingAmountIsNull() {
        // given
        Point point = createPoint(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createPointHistory(
                point,
                point.getUser(),
                null,
                1000,
                null,
                PointHistoryType.CHARGE,
                "충전",
                EXPIRE_AT
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_025);
    }

    @Test
    @DisplayName("생성 실패 - remainingAmount가 음수이면 예외가 발생한다")
    void create_fail_whenRemainingAmountIsNegative() {
        // given
        Point point = createPoint(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createPointHistory(
                point,
                point.getUser(),
                null,
                1000,
                -1,
                PointHistoryType.CHARGE,
                "충전",
                EXPIRE_AT
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_025);
    }

    @Test
    @DisplayName("생성 실패 - type이 null이면 예외가 발생한다")
    void create_fail_whenTypeIsNull() {
        // given
        Point point = createPoint(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createPointHistory(
                point,
                point.getUser(),
                null,
                1000,
                1000,
                null,
                "충전",
                EXPIRE_AT
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_026);
    }

    @Test
    @DisplayName("생성 실패 - CHARGE/EARN/REFUND인데 amount가 0 이하이면 예외가 발생한다")
    void create_fail_whenSourceAmountIsNotPositive() {
        // given
        Point point = createPoint(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createPointHistory(
                point,
                point.getUser(),
                null,
                -1000,
                0,
                PointHistoryType.CHARGE,
                "충전",
                EXPIRE_AT
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_027);
    }

    @Test
    @DisplayName("생성 실패 - CHARGE/EARN/REFUND인데 amount와 remainingAmount가 다르면 예외가 발생한다")
    void create_fail_whenSourceRemainingAmountIsDifferentFromAmount() {
        // given
        Point point = createPoint(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createPointHistory(
                point,
                point.getUser(),
                null,
                1000,
                500,
                PointHistoryType.CHARGE,
                "충전",
                EXPIRE_AT
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_028);
    }

    @Test
    @DisplayName("생성 실패 - CHARGE/EARN/REFUND인데 expireAt이 null이면 예외가 발생한다")
    void charge_fail_whenExpireAtIsNull() {
        // given
        Point point = createPoint(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> PointHistory.charge(
                point,
                1000,
                "충전",
                null
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_029);
    }

    @Test
    @DisplayName("생성 실패 - USE/EXPIRE인데 amount가 음수가 아니면 예외가 발생한다")
    void create_fail_whenUseOrExpireAmountIsNotNegative() {
        // given
        Point point = createPoint(1L);
        Order order = mockOrder(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createPointHistory(
                point,
                point.getUser(),
                order,
                1000,
                0,
                PointHistoryType.USE,
                "사용",
                null
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_030);
    }

    @Test
    @DisplayName("생성 실패 - USE/EXPIRE인데 remainingAmount가 0이 아니면 예외가 발생한다")
    void create_fail_whenUseOrExpireRemainingAmountIsNotZero() {
        // given
        Point point = createPoint(1L);
        Order order = mockOrder(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> createPointHistory(
                point,
                point.getUser(),
                order,
                -1000,
                100,
                PointHistoryType.USE,
                "사용",
                null
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_031);
    }

    // ── decreaseRemainingAmount ──

    @Test
    @DisplayName("생성 실패 - CHARGE인데 amount가 음수이면 remainingAmount 검증에서 예외가 발생한다")
    void charge_fail_whenAmountIsNegative() {
        // given
        Point point = createPoint(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> PointHistory.charge(
                point,
                -1000,
                "충전",
                EXPIRE_AT
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_025);
    }

    @Test
    @DisplayName("use 실패 - amount가 null이면 예외가 발생한다")
    void use_fail_whenAmountIsNull() {
        // given
        Point point = createPoint(1L);
        Order order = mockOrder(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> PointHistory.use(
                point,
                order,
                null,
                "사용"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
    }

    @Test
    @DisplayName("use 실패 - amount가 0이면 예외가 발생한다")
    void use_fail_whenAmountIsZero() {
        // given
        Point point = createPoint(1L);
        Order order = mockOrder(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> PointHistory.use(
                point,
                order,
                0,
                "사용"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
    }

    @Test
    @DisplayName("use 실패 - amount가 음수이면 예외가 발생한다")
    void use_fail_whenAmountIsNegative() {
        // given
        Point point = createPoint(1L);
        Order order = mockOrder(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> PointHistory.use(
                point,
                order,
                -1,
                "사용"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
    }

    @Test
    @DisplayName("expire 실패 - amount가 null이면 예외가 발생한다")
    void expire_fail_whenAmountIsNull() {
        // given
        Point point = createPoint(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> PointHistory.expire(
                point,
                null,
                "만료"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
    }

    @Test
    @DisplayName("expire 실패 - amount가 0이면 예외가 발생한다")
    void expire_fail_whenAmountIsZero() {
        // given
        Point point = createPoint(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> PointHistory.expire(
                point,
                0,
                "만료"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
    }

    // ── expireRemainingAmount ──

    @Test
    @DisplayName("expire 실패 - amount가 음수이면 예외가 발생한다")
    void expire_fail_whenAmountIsNegative() {
        // given
        Point point = createPoint(1L);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> PointHistory.expire(
                point,
                -1,
                "만료"
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
    }

    @Test
    @DisplayName("decreaseRemainingAmount 성공 - 잔여 포인트가 차감된다")
    void decreaseRemainingAmount_success() {
        // given
        Point point = createPoint(1L);
        PointHistory history = chargeHistory(point, 1000);

        // when
        history.decreaseRemainingAmount(400);

        // then
        assertThat(history.getRemainingAmount()).isEqualTo(600);
    }

    // ── helpers ──

    @Test
    @DisplayName("decreaseRemainingAmount 실패 - amount가 null이면 예외가 발생한다")
    void decreaseRemainingAmount_fail_whenAmountIsNull() {
        // given
        Point point = createPoint(1L);
        PointHistory history = chargeHistory(point, 1000);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> history.decreaseRemainingAmount(null)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
        assertThat(history.getRemainingAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("decreaseRemainingAmount 실패 - amount가 0이면 예외가 발생한다")
    void decreaseRemainingAmount_fail_whenAmountIsZero() {
        // given
        Point point = createPoint(1L);
        PointHistory history = chargeHistory(point, 1000);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> history.decreaseRemainingAmount(0)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
        assertThat(history.getRemainingAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("decreaseRemainingAmount 실패 - amount가 음수이면 예외가 발생한다")
    void decreaseRemainingAmount_fail_whenAmountIsNegative() {
        // given
        Point point = createPoint(1L);
        PointHistory history = chargeHistory(point, 1000);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> history.decreaseRemainingAmount(-1)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_003);
        assertThat(history.getRemainingAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("decreaseRemainingAmount 실패 - 차감 가능한 이력이 아니면 예외가 발생한다")
    void decreaseRemainingAmount_fail_whenNotDeductibleSource() {
        // given
        Point point = createPoint(1L);
        PointHistory history = useHistory(point, 1000);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> history.decreaseRemainingAmount(500)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_032);
        assertThat(history.getRemainingAmount()).isEqualTo(0);
    }

    @Test
    @DisplayName("decreaseRemainingAmount 실패 - 잔여 포인트보다 큰 금액이면 예외가 발생한다")
    void decreaseRemainingAmount_fail_whenAmountExceedsRemaining() {
        // given
        Point point = createPoint(1L);
        PointHistory history = chargeHistory(point, 1000);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> history.decreaseRemainingAmount(1001)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_033);
        assertThat(history.getRemainingAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("expireRemainingAmount 성공 - 잔여 포인트를 0으로 변경하고 만료 금액을 반환한다")
    void expireRemainingAmount_success() {
        // given
        Point point = createPoint(1L);
        PointHistory history = chargeHistory(point, 1000);

        // when
        Integer expiredAmount = history.expireRemainingAmount();

        // then
        assertThat(expiredAmount).isEqualTo(1000);
        assertThat(history.getRemainingAmount()).isEqualTo(0);
    }

    @Test
    @DisplayName("expireRemainingAmount 실패 - 만료 가능한 이력이 아니면 예외가 발생한다")
    void expireRemainingAmount_fail_whenNotDeductibleSource() {
        // given
        Point point = createPoint(1L);
        PointHistory history = useHistory(point, 1000);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            history::expireRemainingAmount
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_034);
    }

}
