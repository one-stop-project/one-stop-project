package com.sparta.one_stop.domain.point.batch;

import com.sparta.one_stop.domain.point.entity.Point;
import com.sparta.one_stop.domain.point.entity.PointHistory;
import com.sparta.one_stop.domain.point.repository.PointHistoryRepository;
import com.sparta.one_stop.domain.point.repository.PointRepository;
import com.sparta.one_stop.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;

import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointExpireBatchConfigTest {

    private static final LocalDate EXECUTION_DATE = LocalDate.of(2026, 6, 17);

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Mock
    private PointRepository pointRepository;

    @Test
    @DisplayName("실행일보다 이전에 만료된 포인트는 만료 Command로 변환한다")
    void processor_processes_history_expired_before_execution_date() throws Exception {
        PointExpireBatchConfig config = new PointExpireBatchConfig(entityManagerFactory);
        ItemProcessor<PointHistory, PointExpireBatchConfig.ExpireCommand> processor =
            config.expireProcessor(EXECUTION_DATE.toString());

        Point point = mock(Point.class);
        PointHistory history = mock(PointHistory.class);

        when(point.getId()).thenReturn(10L);
        when(history.getId()).thenReturn(100L);
        when(history.getPoint()).thenReturn(point);
        when(history.getExpireAt()).thenReturn(EXECUTION_DATE.minusDays(1));
        when(history.getRemainingAmount()).thenReturn(300);

        PointExpireBatchConfig.ExpireCommand result = processor.process(history);

        assertThat(result).isEqualTo(
            new PointExpireBatchConfig.ExpireCommand(100L, 10L, 300)
        );
    }

    @Test
    @DisplayName("오늘 만료되는 포인트는 오늘까지 유효하므로 만료하지 않는다")
    void processor_skips_history_expiring_on_execution_date() throws Exception {
        PointExpireBatchConfig config = new PointExpireBatchConfig(entityManagerFactory);
        ItemProcessor<PointHistory, PointExpireBatchConfig.ExpireCommand> processor =
            config.expireProcessor(EXECUTION_DATE.toString());

        PointHistory history = mock(PointHistory.class);
        when(history.getExpireAt()).thenReturn(EXECUTION_DATE);

        PointExpireBatchConfig.ExpireCommand result = processor.process(history);

        assertThat(result).isNull();
        verify(history, never()).getRemainingAmount();
    }

    @Test
    @DisplayName("미래 만료 포인트는 만료하지 않는다")
    void processor_skips_history_expiring_after_execution_date() throws Exception {
        PointExpireBatchConfig config = new PointExpireBatchConfig(entityManagerFactory);
        ItemProcessor<PointHistory, PointExpireBatchConfig.ExpireCommand> processor =
            config.expireProcessor(EXECUTION_DATE.toString());

        PointHistory history = mock(PointHistory.class);
        when(history.getExpireAt()).thenReturn(EXECUTION_DATE.plusDays(1));

        assertThat(processor.process(history)).isNull();
    }

    @Test
    @DisplayName("Writer는 DB에서 다시 조회한 실제 잔여 포인트만 만료한다")
    void writer_expires_using_fresh_database_state() throws Exception {
        PointExpireBatchConfig config = new PointExpireBatchConfig(entityManagerFactory);
        ItemWriter<PointExpireBatchConfig.ExpireCommand> writer =
            config.expireWriter(pointHistoryRepository, pointRepository);

        User user = mock(User.class);
        Point freshPoint = mock(Point.class);
        PointHistory freshTarget = mock(PointHistory.class);

        when(freshPoint.getId()).thenReturn(10L);
        when(freshPoint.getUser()).thenReturn(user);
        when(freshTarget.getId()).thenReturn(100L);
        when(freshTarget.expireRemainingAmount()).thenReturn(250);
        when(pointRepository.findAllById(anyCollection())).thenReturn(List.of(freshPoint));
        when(pointHistoryRepository.findAllById(anyCollection())).thenReturn(List.of(freshTarget));

        writer.write(new Chunk<>(List.of(
            new PointExpireBatchConfig.ExpireCommand(100L, 10L, 999)
        )));

        verify(freshPoint).decreaseBalance(250);
        verify(pointRepository).saveAll(List.of(freshPoint));
        verify(pointHistoryRepository).saveAll(List.of(freshTarget));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PointHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(pointHistoryRepository).saveAll(captor.capture());

        List<PointHistory> savedExpireHistories = captor.getAllValues().stream()
            .filter(list -> !list.isEmpty() && list.get(0) != freshTarget)
            .findFirst()
            .orElseThrow();

        PointHistory expireHistory = savedExpireHistories.get(0);
        assertThat(expireHistory.getAmount()).isEqualTo(-250);
        assertThat(expireHistory.getRemainingAmount()).isZero();
    }

    @Test
    @DisplayName("이미 만료 처리된 원본은 재실행해도 잔액을 다시 차감하지 않는다")
    void writer_is_idempotent_when_target_is_already_expired() throws Exception {
        PointExpireBatchConfig config = new PointExpireBatchConfig(entityManagerFactory);
        ItemWriter<PointExpireBatchConfig.ExpireCommand> writer =
            config.expireWriter(pointHistoryRepository, pointRepository);

        Point freshPoint = mock(Point.class);
        PointHistory alreadyExpiredTarget = mock(PointHistory.class);

        when(freshPoint.getId()).thenReturn(10L);
        when(alreadyExpiredTarget.getId()).thenReturn(100L);
        when(alreadyExpiredTarget.expireRemainingAmount()).thenReturn(0);
        when(pointRepository.findAllById(anyCollection())).thenReturn(List.of(freshPoint));
        when(pointHistoryRepository.findAllById(anyCollection()))
            .thenReturn(List.of(alreadyExpiredTarget));

        writer.write(new Chunk<>(List.of(
            new PointExpireBatchConfig.ExpireCommand(100L, 10L, 300)
        )));

        verify(freshPoint, never()).decreaseBalance(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PointHistory>> captor = ArgumentCaptor.forClass(List.class);
        verify(pointHistoryRepository, org.mockito.Mockito.times(2)).saveAll(captor.capture());

        assertThat(captor.getAllValues()).anyMatch(List::isEmpty);
    }
}
