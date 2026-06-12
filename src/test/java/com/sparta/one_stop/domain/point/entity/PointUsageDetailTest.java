package com.sparta.one_stop.domain.point.entity;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.point.util.PointIntegrityHasher;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointUsageDetailTest {

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

    private static PointHistory expireHistory(
        Point point,
        int amount
    ) {
        return PointHistory.expire(
            point,
            amount,
            "테스트 만료"
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

    @Test
    @DisplayName("생성 성공 - useHistory, sourceHistory, usedAmount, sourceExpireAt이 설정된다")
    void create_success() {
        // given
        Point point = createPoint(1L);
        PointHistory useHistory = useHistory(
            point,
            300
        );
        PointHistory sourceHistory = chargeHistory(
            point,
            1000
        );

        // when
        PointUsageDetail usageDetail = new PointUsageDetail(
            useHistory,
            sourceHistory,
            300
        );

        // then
        assertThat(usageDetail.getUseHistory()).isSameAs(useHistory);
        assertThat(usageDetail.getSourceHistory()).isSameAs(sourceHistory);
        assertThat(usageDetail.getUsedAmount()).isEqualTo(300);
        assertThat(usageDetail.getSourceExpireAt()).isEqualTo(EXPIRE_AT);
    }

    @Test
    @DisplayName("생성 실패 - useHistory가 null이면 예외가 발생한다")
    void create_fail_whenUseHistoryIsNull() {
        // given
        Point point = createPoint(1L);
        PointHistory sourceHistory = chargeHistory(
            point,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new PointUsageDetail(
                null,
                sourceHistory,
                300
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_035);
    }

    @Test
    @DisplayName("생성 실패 - sourceHistory가 null이면 예외가 발생한다")
    void create_fail_whenSourceHistoryIsNull() {
        // given
        Point point = createPoint(1L);
        PointHistory useHistory = useHistory(
            point,
            300
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new PointUsageDetail(
                useHistory,
                null,
                300
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_036);
    }

    @Test
    @DisplayName("생성 실패 - useHistory의 type이 USE가 아니면 예외가 발생한다")
    void create_fail_whenUseHistoryTypeIsNotUse() {
        // given
        Point point = createPoint(1L);
        PointHistory useHistory = chargeHistory(
            point,
            1000
        );
        PointHistory sourceHistory = chargeHistory(
            point,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new PointUsageDetail(
                useHistory,
                sourceHistory,
                300
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_037);
    }

    @Test
    @DisplayName("생성 실패 - sourceHistory가 차감 대상이 아니면 예외가 발생한다")
    void create_fail_whenSourceHistoryIsNotDeductibleSource() {
        // given
        Point point = createPoint(1L);
        PointHistory useHistory = useHistory(
            point,
            300
        );
        PointHistory sourceHistory = useHistory(
            point,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new PointUsageDetail(
                useHistory,
                sourceHistory,
                300
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_038);
    }

    @Test
    @DisplayName("생성 실패 - EXPIRE 이력은 차감 대상이 아니므로 예외가 발생한다")
    void create_fail_whenSourceHistoryIsExpireType() {
        // given
        Point point = createPoint(1L);
        PointHistory useHistory = useHistory(
            point,
            300
        );
        PointHistory sourceHistory = expireHistory(
            point,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new PointUsageDetail(
                useHistory,
                sourceHistory,
                300
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_038);
    }

    @Test
    @DisplayName("생성 실패 - usedAmount가 null이면 예외가 발생한다")
    void create_fail_whenUsedAmountIsNull() {
        // given
        Point point = createPoint(1L);
        PointHistory useHistory = useHistory(
            point,
            300
        );
        PointHistory sourceHistory = chargeHistory(
            point,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new PointUsageDetail(
                useHistory,
                sourceHistory,
                null
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_039);
    }

    @Test
    @DisplayName("생성 실패 - usedAmount가 0이면 예외가 발생한다")
    void create_fail_whenUsedAmountIsZero() {
        // given
        Point point = createPoint(1L);
        PointHistory useHistory = useHistory(
            point,
            300
        );
        PointHistory sourceHistory = chargeHistory(
            point,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new PointUsageDetail(
                useHistory,
                sourceHistory,
                0
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_039);
    }

    @Test
    @DisplayName("생성 실패 - usedAmount가 음수이면 예외가 발생한다")
    void create_fail_whenUsedAmountIsNegative() {
        // given
        Point point = createPoint(1L);
        PointHistory useHistory = useHistory(
            point,
            300
        );
        PointHistory sourceHistory = chargeHistory(
            point,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new PointUsageDetail(
                useHistory,
                sourceHistory,
                -1
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_039);
    }

    @Test
    @DisplayName("생성 실패 - sourceHistory의 remainingAmount가 null이면 예외가 발생한다")
    void create_fail_whenSourceRemainingAmountIsNull() {
        // given
        Point point = createPoint(1L);
        PointHistory useHistory = useHistory(
            point,
            300
        );
        PointHistory sourceHistory = mock(PointHistory.class);

        when(sourceHistory.isDeductibleSource()).thenReturn(true);
        when(sourceHistory.getRemainingAmount()).thenReturn(null);
        when(sourceHistory.getExpireAt()).thenReturn(EXPIRE_AT);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new PointUsageDetail(
                useHistory,
                sourceHistory,
                300
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_040);
    }

    @Test
    @DisplayName("생성 실패 - usedAmount가 sourceHistory의 remainingAmount를 초과하면 예외가 발생한다")
    void create_fail_whenUsedAmountExceedsSourceRemainingAmount() {
        // given
        Point point = createPoint(1L);
        PointHistory useHistory = useHistory(
            point,
            1001
        );
        PointHistory sourceHistory = chargeHistory(
            point,
            1000
        );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new PointUsageDetail(
                useHistory,
                sourceHistory,
                1001
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_040);
        assertThat(sourceHistory.getRemainingAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("생성 실패 - sourceHistory의 expireAt이 null이면 예외가 발생한다")
    void create_fail_whenSourceExpireAtIsNull() {
        // given
        Point point = createPoint(1L);
        PointHistory useHistory = useHistory(
            point,
            300
        );
        PointHistory sourceHistory = mock(PointHistory.class);

        when(sourceHistory.isDeductibleSource()).thenReturn(true);
        when(sourceHistory.getRemainingAmount()).thenReturn(1000);
        when(sourceHistory.getExpireAt()).thenReturn(null);

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> new PointUsageDetail(
                useHistory,
                sourceHistory,
                300
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POINT_041);
    }

}
