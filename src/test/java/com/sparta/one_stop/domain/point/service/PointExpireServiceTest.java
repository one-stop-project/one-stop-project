package com.sparta.one_stop.domain.point.service;

import com.sparta.one_stop.domain.point.entity.Point;
import com.sparta.one_stop.domain.point.entity.PointHistory;
import com.sparta.one_stop.domain.point.repository.PointHistoryRepository;
import com.sparta.one_stop.domain.point.util.PointIntegrityHasher;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointExpireServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 5);
    private static final LocalDate EXPIRE_AT = LocalDate.of(2026, 6, 5);

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @InjectMocks
    private PointExpireService pointExpireService;

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
        User user,
        int balance
    ) {
        Point point = Point.createInitial(user);

        if (balance > 0) {
            point.increaseBalance(balance);
        }

        return point;
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
    @DisplayName("expirePoints 성공 - 만료 대상 이력의 remainingAmount를 0으로 변경하고 balance를 차감한다")
    void expirePoints_success() {
        // given
        User user = mockUser(1L);
        Point point = createPointWithBalance(
            user,
            3000
        );

        PointHistory targetHistory1 = chargeHistory(
            point,
            1000
        );

        PointHistory targetHistory2 = chargeHistory(
            point,
            2000
        );

        when(pointHistoryRepository.findExpireTargetHistories(
            any(),
            any()
        )).thenReturn(List.of(
            targetHistory1,
            targetHistory2
        ));

        // when
        int expiredAmount = pointExpireService.expirePoints(TODAY);

        // then
        assertThat(expiredAmount).isEqualTo(3000);
        assertThat(point.getBalance()).isEqualTo(0);
        assertThat(targetHistory1.getRemainingAmount()).isEqualTo(0);
        assertThat(targetHistory2.getRemainingAmount()).isEqualTo(0);

        ArgumentCaptor<List> expireHistoriesCaptor =
            ArgumentCaptor.forClass(List.class);

        verify(pointHistoryRepository).saveAll(expireHistoriesCaptor.capture());

        List<PointHistory> expireHistories = expireHistoriesCaptor.getValue();

        assertThat(expireHistories).hasSize(2);
        assertThat(expireHistories.get(0).getType()).isEqualTo(PointHistoryType.EXPIRE);
        assertThat(expireHistories.get(0).getAmount()).isEqualTo(-1000);
        assertThat(expireHistories.get(0).getRemainingAmount()).isEqualTo(0);
        assertThat(expireHistories.get(0).getPoint()).isSameAs(point);

        assertThat(expireHistories.get(1).getType()).isEqualTo(PointHistoryType.EXPIRE);
        assertThat(expireHistories.get(1).getAmount()).isEqualTo(-2000);
        assertThat(expireHistories.get(1).getRemainingAmount()).isEqualTo(0);
        assertThat(expireHistories.get(1).getPoint()).isSameAs(point);
    }

    @Test
    @DisplayName("expirePoints 성공 - 만료 대상이 없으면 0을 반환한다")
    void expirePoints_success_whenExpireTargetsAreEmpty() {
        // given
        when(pointHistoryRepository.findExpireTargetHistories(
            any(),
            any()
        )).thenReturn(List.of());

        // when
        int expiredAmount = pointExpireService.expirePoints(TODAY);

        // then
        assertThat(expiredAmount).isEqualTo(0);

        verify(pointHistoryRepository).findExpireTargetHistories(
            any(),
            any()
        );
        verify(pointHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("expirePoints 성공 - remainingAmount가 0인 이력은 건너뛴다")
    void expirePoints_success_skipWhenRemainingAmountIsZero() {
        // given
        User user = mockUser(1L);
        Point point = createPointWithBalance(
            user,
            1000
        );

        PointHistory targetHistory = chargeHistory(
            point,
            1000
        );

        targetHistory.expireRemainingAmount();

        when(pointHistoryRepository.findExpireTargetHistories(
            any(),
            any()
        )).thenReturn(List.of(targetHistory));

        // when
        int expiredAmount = pointExpireService.expirePoints(TODAY);

        // then
        assertThat(expiredAmount).isEqualTo(0);
        assertThat(point.getBalance()).isEqualTo(1000);
        assertThat(targetHistory.getRemainingAmount()).isEqualTo(0);

        ArgumentCaptor<List> expireHistoriesCaptor =
            ArgumentCaptor.forClass(List.class);

        verify(pointHistoryRepository).saveAll(expireHistoriesCaptor.capture());

        assertThat(expireHistoriesCaptor.getValue()).isEmpty();
    }

}
