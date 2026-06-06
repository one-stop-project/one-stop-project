package com.sparta.one_stop.domain.point.entity;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.point.util.PointIntegrityHasher;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private static User mockUser(Long userId) {
        User user = mock(User.class);

        when(user.getId()).thenReturn(userId);

        return user;
    }

    private static Order mockOrder(Long orderId) {
        Order order = mock(Order.class);

        when(order.getId()).thenReturn(orderId);

        return order;
    }

    private static Point createPoint(Long userId) {
        return Point.createInitial(mockUser(userId));
    }

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

    // ── charge ──

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

    // ── earn ──

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
        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getAmount()).isEqualTo(500);
        assertThat(history.getRemainingAmount()).isEqualTo(500);
        assertThat(history.getType()).isEqualTo(PointHistoryType.EARN);
        assertThat(history.getExpireAt()).isEqualTo(EXPIRE_AT);
    }

    // ── use ──

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
        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getAmount()).isEqualTo(-2000);
        assertThat(history.getRemainingAmount()).isEqualTo(0);
        assertThat(history.getType()).isEqualTo(PointHistoryType.USE);
        assertThat(history.getExpireAt()).isNull();
    }

    // ── refund ──

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
        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getAmount()).isEqualTo(1000);
        assertThat(history.getRemainingAmount()).isEqualTo(1000);
        assertThat(history.getType()).isEqualTo(PointHistoryType.REFUND);
        assertThat(history.getExpireAt()).isEqualTo(originalExpireAt);
    }

    // ── expire ──

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
        assertThat(history.getOrder()).isNull();
        assertThat(history.getAmount()).isEqualTo(-500);
        assertThat(history.getRemainingAmount()).isEqualTo(0);
        assertThat(history.getType()).isEqualTo(PointHistoryType.EXPIRE);
        assertThat(history.getExpireAt()).isNull();
    }

    // ── 필수값 검증 (charge 기준) ──

    @Test
    @DisplayName("생성 실패 - point가 null이면 예외가 발생한다")
    void charge_fail_whenPointIsNull() {
        assertThatThrownBy(() -> PointHistory.charge(
            null,
            1000,
            "충전",
            EXPIRE_AT
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("포인트 계정은 필수입니다.");
    }

    @Test
    @DisplayName("생성 실패 - amount가 null이면 예외가 발생한다")
    void charge_fail_whenAmountIsNull() {
        // given
        Point point = createPoint(1L);

        // when & then
        assertThatThrownBy(() -> PointHistory.charge(
            point,
            null,
            "충전",
            EXPIRE_AT
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("포인트 변동 금액은 0일 수 없습니다.");
    }

    @Test
    @DisplayName("생성 실패 - amount가 0이면 예외가 발생한다")
    void charge_fail_whenAmountIsZero() {
        // given
        Point point = createPoint(1L);

        // when & then
        assertThatThrownBy(() -> PointHistory.charge(
            point,
            0,
            "충전",
            EXPIRE_AT
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("포인트 변동 금액은 0일 수 없습니다.");
    }

    // ── 유형별 정책 검증 ──

    @Test
    @DisplayName("생성 실패 - CHARGE인데 amount가 음수이면 예외가 발생한다")
    void charge_fail_whenAmountIsNegative() {
        // given
        Point point = createPoint(1L);

        // when & then
        assertThatThrownBy(() -> PointHistory.charge(
            point,
            -1000,
            "충전",
            EXPIRE_AT
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("잔여 포인트는 0 이상이어야 합니다.");
    }

    @Test
    @DisplayName("생성 실패 - CHARGE인데 expireAt이 null이면 예외가 발생한다")
    void charge_fail_whenExpireAtIsNull() {
        // given
        Point point = createPoint(1L);

        // when & then
        assertThatThrownBy(() -> PointHistory.charge(
            point,
            1000,
            "충전",
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("포인트 만료일은 필수입니다.");
    }

    @Test
    @DisplayName("생성 실패 - USE인데 amount가 null이면 예외가 발생한다")
    void use_fail_whenAmountIsNull() {
        // given
        Point point = createPoint(1L);
        Order order = mockOrder(1L);

        // when & then
        assertThatThrownBy(() -> PointHistory.use(
            point,
            order,
            null,
            "사용"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("포인트 금액은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("생성 실패 - USE인데 amount가 0이면 예외가 발생한다")
    void use_fail_whenAmountIsZero() {
        // given
        Point point = createPoint(1L);
        Order order = mockOrder(1L);

        // when & then
        assertThatThrownBy(() -> PointHistory.use(
            point,
            order,
            0,
            "사용"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("포인트 금액은 1 이상이어야 합니다.");
    }

    @Test
    @DisplayName("생성 실패 - USE인데 amount가 음수이면 예외가 발생한다")
    void use_fail_whenAmountIsNegative() {
        // given
        Point point = createPoint(1L);
        Order order = mockOrder(1L);

        // when & then
        assertThatThrownBy(() -> PointHistory.use(
            point,
            order,
            -1,
            "사용"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("포인트 금액은 1 이상이어야 합니다.");
    }

    // ── decreaseRemainingAmount ──

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

    @Test
    @DisplayName("decreaseRemainingAmount 실패 - 차감 가능한 이력이 아니면 예외가 발생한다")
    void decreaseRemainingAmount_fail_whenNotDeductibleSource() {
        // given
        Point point = createPoint(1L);
        PointHistory history = useHistory(point, 1000);

        // when & then
        assertThatThrownBy(() -> history.decreaseRemainingAmount(500))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("차감 가능한 포인트 이력이 아닙니다.");

        assertThat(history.getRemainingAmount()).isEqualTo(0);
    }

    @Test
    @DisplayName("decreaseRemainingAmount 실패 - 잔여 포인트보다 큰 금액이면 예외가 발생한다")
    void decreaseRemainingAmount_fail_whenAmountExceedsRemaining() {
        // given
        Point point = createPoint(1L);
        PointHistory history = chargeHistory(point, 1000);

        // when & then
        assertThatThrownBy(() -> history.decreaseRemainingAmount(1001))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("잔여 포인트가 부족합니다.");

        assertThat(history.getRemainingAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("decreaseRemainingAmount 실패 - amount가 0 이하이면 예외가 발생한다")
    void decreaseRemainingAmount_fail_whenAmountIsZeroOrNegative() {
        // given
        Point point = createPoint(1L);
        PointHistory history = chargeHistory(point, 1000);

        // when & then
        assertThatThrownBy(() -> history.decreaseRemainingAmount(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("포인트 금액은 1 이상이어야 합니다.");

        assertThat(history.getRemainingAmount()).isEqualTo(1000);
    }

    // ── expireRemainingAmount ──

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

        // when & then
        assertThatThrownBy(() -> history.expireRemainingAmount())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("만료 가능한 포인트 이력이 아닙니다.");
    }

}
