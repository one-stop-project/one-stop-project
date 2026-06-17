package com.sparta.one_stop.domain.point.batch;

import com.sparta.one_stop.domain.point.entity.Point;
import com.sparta.one_stop.domain.point.entity.PointHistory;
import com.sparta.one_stop.domain.point.repository.PointHistoryRepository;
import com.sparta.one_stop.domain.point.repository.PointRepository;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class PointExpireBatchConfig {

    public static final int CHUNK_SIZE = 500;

    private final EntityManagerFactory entityManagerFactory;

    @Bean
    public Job pointExpireJob(JobRepository jobRepository, Step pointExpireStep) {
        return new JobBuilder("pointExpireJob", jobRepository)
            .start(pointExpireStep)
            .build();
    }

    @Bean
    public Step pointExpireStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        JpaCursorItemReader<PointHistory> reader,
        ItemProcessor<PointHistory, ExpireCommand> processor,
        ItemWriter<ExpireCommand> writer) {

        return new StepBuilder("pointExpireStep", jobRepository)
            .<PointHistory, ExpireCommand>chunk(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .retryLimit(3)
            .retry(ObjectOptimisticLockingFailureException.class)
            .skipLimit(100)
            .noSkip(DataIntegrityViolationException.class)
            .noSkip(NullPointerException.class)
            .listener(new BatchSkipListener())
            .build();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Reader — Cursor 유지 + JOIN FETCH
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Bean
    @StepScope
    public JpaCursorItemReader<PointHistory> expireTargetReader(
        @Value("#{jobParameters['expireDate']}") String expireDateStr) {

        LocalDate expireDate = (expireDateStr != null && !expireDateStr.isBlank())
            ? LocalDate.parse(expireDateStr)
            : LocalDate.now();

        Map<String, Object> params = new HashMap<>();
        params.put("types", List.of(PointHistoryType.CHARGE, PointHistoryType.EARN, PointHistoryType.REFUND));
        params.put("expireDate", expireDate);

        return new JpaCursorItemReaderBuilder<PointHistory>()
            .name("expireTargetReader")
            .entityManagerFactory(entityManagerFactory)
            .queryString("""
                        SELECT ph FROM PointHistory ph
                         JOIN FETCH ph.point
                         WHERE ph.type IN :types
                           AND ph.remainingAmount > 0
                           AND ph.expireAt IS NOT NULL
                           AND ph.expireAt < :expireDate
                         ORDER BY ph.id ASC
                        """)
            .parameterValues(params)
            .build();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Processor — 순수 함수 (상태 변경 X)
    //  정책: expireAt 당일은 사용 가능, 다음 날부터 만료 대상
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Bean
    @StepScope
    public ItemProcessor<PointHistory, ExpireCommand> expireProcessor(
        @Value("#{jobParameters['expireDate']}") String expireDateStr) {

        LocalDate executionDate = (expireDateStr != null && !expireDateStr.isBlank())
            ? LocalDate.parse(expireDateStr)
            : LocalDate.now();

        return targetHistory -> {
            LocalDate expireAt = targetHistory.getExpireAt();

            // 오늘 만료되는 포인트는 오늘 23:59:59까지 사용 가능하다.
            // Reader 조건과 별개로 Processor에서도 경계 정책을 재검증한다.
            if (expireAt == null || !expireAt.isBefore(executionDate)) {
                return null;
            }

            int expiredAmount = targetHistory.getRemainingAmount();
            if (expiredAmount <= 0) {
                return null;
            }

            return new ExpireCommand(
                targetHistory.getId(),
                targetHistory.getPoint().getId(),
                expiredAmount // 참고용이며 실제 차감은 Writer에서 최신 엔티티 기준으로 재계산
            );
        };
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Writer — 진실의 공급원(Fresh Entity) 기반 상태 변경
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Bean
    @StepScope
    public ItemWriter<ExpireCommand> expireWriter(
        PointHistoryRepository pointHistoryRepository,
        PointRepository pointRepository) {

        return chunk -> {
            Set<Long> pointIds = new HashSet<>();
            Set<Long> targetHistoryIds = new HashSet<>();
            Map<Long, Long> historyToPointMap = new HashMap<>();

            // 1. Command로부터 ID와 매핑 정보만 추출 (Command.amount는 신뢰하지 않음!)
            for (ExpireCommand cmd : chunk) {
                pointIds.add(cmd.pointId());
                targetHistoryIds.add(cmd.targetHistoryId());
                historyToPointMap.put(cmd.targetHistoryId(), cmd.pointId()); // N+1 방지용 매핑
            }

            // 2. DB에서 무조건 최신 상태(Fresh) 데이터 조회
            List<Point> freshPoints = pointRepository.findAllById(pointIds);
            List<PointHistory> freshTargetHistories = pointHistoryRepository.findAllById(targetHistoryIds);

            Map<Long, Point> freshPointMap = freshPoints.stream()
                .collect(Collectors.toMap(Point::getId, p -> p));

            List<PointHistory> expireHistories = new ArrayList<>();
            Map<Long, Integer> actualDeductAmounts = new HashMap<>(); // ★ 실제 차감액 집계용

            // 3. [핵심 수정] Fresh TargetHistory 기준으로 실제 만료된 금액만 집계
            for (PointHistory freshTh : freshTargetHistories) {
                // 진실의 공급원: 현재 DB 상태를 기준으로 남은 금액 만료
                int actualAmount = freshTh.expireRemainingAmount();

                // 만약 재시도 시점이나 동시성에 의해 이미 만료액이 0이라면 무시
                if (actualAmount <= 0) {
                    continue;
                }

                Long pointId = historyToPointMap.get(freshTh.getId());
                actualDeductAmounts.merge(pointId, actualAmount, Integer::sum); // ★ 실제 값 누적

                Point freshPoint = freshPointMap.get(pointId);
                expireHistories.add(PointHistory.expire(
                    freshPoint,
                    actualAmount,
                    "포인트 만료 (원본 ID: " + freshTh.getId() + ")"
                ));
            }

            // 4. 집계된 실제 만료 금액(actualDeductAmounts)으로 Point 일괄 차감
            for (Point freshPoint : freshPoints) {
                int deductAmount = actualDeductAmounts.getOrDefault(freshPoint.getId(), 0);
                if (deductAmount > 0) { // 0원 차감으로 인한 불필요한 해시 재계산 방지
                    freshPoint.decreaseBalance(deductAmount);
                }
            }

            // 5. 안전하게 일괄 저장 (Merge/Insert)
            pointRepository.saveAll(freshPoints);
            pointHistoryRepository.saveAll(freshTargetHistories);
            pointHistoryRepository.saveAll(expireHistories);

            log.info("[POINT_EXPIRE] chunk written: resultCount={}, uniquePoints={}",
                chunk.size(), freshPoints.size());
        };
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  보조 타입 — 불변 Command 객체
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    public record ExpireCommand(
        Long targetHistoryId,
        Long pointId,
        int amount
    ) {}
}
