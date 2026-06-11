package com.sparta.one_stop.domain.point.batch;

import com.sparta.one_stop.domain.point.entity.Point;
import com.sparta.one_stop.domain.point.entity.PointHistory;
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
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 포인트 만료 배치 — Spring Batch + Cursor 기반
 *
 * <p><b>왜 Cursor 방식인가</b>
 * <ul>
 *   <li>기존 구현은 {@code findExpireTargetHistories()} → 결과 전체를 메모리에 적재 → OOM 위험</li>
 *   <li>JpaCursorItemReader는 ResultSet 커서로 한 건씩 흘려보냄 → 메모리 평탄</li>
 *   <li>수백만 건 만료 시에도 안전</li>
 * </ul>
 *
 * <p><b>왜 Chunk 처리인가 (백프레셔)</b>
 * <ul>
 *   <li>{@code CHUNK_SIZE}개씩 트랜잭션 분리 → 한 트랜잭션이 너무 길어지지 않음</li>
 *   <li>중간 실패 시 이전 청크는 커밋된 상태 유지 → 재실행 시 처음부터 안 함</li>
 *   <li>DB 락 점유 시간 최소화 → 운영 트래픽 영향 최소화</li>
 * </ul>
 *
 * <p><b>실행</b>
 * <pre>
 *   // PointExpireScheduler에서:
 *   jobLauncher.run(pointExpireJob,
 *       new JobParametersBuilder()
 *           .addString("expireDate", LocalDate.now().toString())
 *           .addLong("timestamp", System.currentTimeMillis())  // 재실행 가능하도록
 *           .toJobParameters());
 * </pre>
 *
 * <p><b>build.gradle 추가 필요</b>
 * <pre>
 *   implementation 'org.springframework.boot:spring-boot-starter-batch'
 * </pre>
 * <br>그리고 메인 클래스에 {@code @EnableBatchProcessing} 추가
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PointExpireBatchConfig {

    /** Chunk 크기 — DB 부하와 처리 속도 균형. 운영에서 모니터링하며 조정 */
    public static final int CHUNK_SIZE = 500;

    private final EntityManagerFactory entityManagerFactory;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Job 정의
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Bean
    public Job pointExpireJob(JobRepository jobRepository, Step pointExpireStep) {
        return new JobBuilder("pointExpireJob", jobRepository)
            .start(pointExpireStep)
            .build();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Step 정의 — Reader / Processor / Writer
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    @Bean
    public Step pointExpireStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        JpaPagingItemReader<PointHistory> reader,
        ItemProcessor<PointHistory, ExpireResult> processor,
        ItemWriter<ExpireResult> writer) {

        return new StepBuilder("pointExpireStep", jobRepository)
            .<PointHistory, ExpireResult>chunk(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .retryLimit(3)
            .retry(ObjectOptimisticLockingFailureException.class)
            .skipLimit(100)                         // 최대 100건까지 개별 실패 허용
            .noSkip(DataIntegrityViolationException.class)
            .noSkip(NullPointerException.class)
            .listener(new BatchSkipListener())
            .build();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Reader — Paging 기반 (청크 트랜잭션 EM 합류)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 만료 대상 조회 — 페이지 단위 조회
     *
     * <p><b>왜 Cursor가 아니라 Paging인가 (★ 무한 만료 버그 수정)</b>
     * <pre>
     *   JpaCursorItemReader는 자체 EntityManager로 커서를 열어,
     *   조회된 엔티티가 청크 트랜잭션의 영속성 컨텍스트에 들어가지 않는다.
     *   → Processor에서 point.decreaseBalance() / expireRemainingAmount()로
     *     상태를 바꿔도 더티 체킹이 안 일어나 flush 누락
     *   → balance/remainingAmount가 DB에 반영 안 됨
     *   → 매 실행 같은 행을 다시 만료 처리 (EXPIRE 이력 중복 + balance 영구 손상)
     *
     *   JpaPagingItemReader는 페이지마다 청크 트랜잭션의 EM을 통해 조회하므로
     *   조회 엔티티가 영속 상태 → 변경이 더티 체킹으로 flush된다.
     *
     *   pageSize는 chunkSize와 동일하게 맞춰 페이지-청크 경계를 일치시킨다.
     * </pre>
     */
    @Bean
    @StepScope
    public JpaPagingItemReader<PointHistory> expireTargetReader(
        @Value("#{jobParameters['expireDate']}") String expireDateStr) {

        LocalDate expireDate = (expireDateStr != null && !expireDateStr.isBlank())
            ? LocalDate.parse(expireDateStr)
            : LocalDate.now();

        Map<String, Object> params = new HashMap<>();
        params.put("types", List.of(PointHistoryType.CHARGE, PointHistoryType.EARN, PointHistoryType.REFUND));
        params.put("expireDate", expireDate);

        return new JpaPagingItemReaderBuilder<PointHistory>()
            .name("expireTargetReader")
            .entityManagerFactory(entityManagerFactory)
            .pageSize(CHUNK_SIZE)  // 청크 크기와 일치 → 페이지-청크 경계 정렬
            .queryString("""
                        SELECT ph FROM PointHistory ph
                         WHERE ph.type IN :types
                           AND ph.remainingAmount > 0
                           AND ph.expireAt IS NOT NULL
                           AND ph.expireAt <= :expireDate
                         ORDER BY ph.id ASC
                        """)
            .parameterValues(params)
            .saveState(false)
            .build();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Processor — 만료 처리 (도메인 로직)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 각 PointHistory에 대해:
     *   1) remainingAmount만큼 만료 처리
     *   2) Point의 balance에서 차감
     *   3) 만료 PointHistory 생성 (Writer에서 저장)
     */
    @Bean
    @StepScope
    public ItemProcessor<PointHistory, ExpireResult> expireProcessor() {
        return targetHistory -> {
            int expiredAmount = targetHistory.expireRemainingAmount();
            if (expiredAmount == 0) {
                return null; // null 반환 시 Writer에서 스킵
            }

            Point point = targetHistory.getPoint();
            point.decreaseBalance(expiredAmount);

            PointHistory expireHistory = PointHistory.expire(
                point,
                expiredAmount,
                "포인트 만료 (원본 ID: " + targetHistory.getId() + ")"
            );

            return new ExpireResult(expireHistory, expiredAmount);
        };
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  Writer — Chunk 단위 저장
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * CHUNK_SIZE 만큼 모인 결과를 한 번에 저장
     */
    @Bean
    @StepScope
    public ItemWriter<ExpireResult> expireWriter(
        org.springframework.data.jpa.repository.JpaRepository<PointHistory, Long> repo) {

        return chunk -> {
            int totalExpired = 0;
            for (ExpireResult result : chunk) {
                repo.save(result.expireHistory());
                totalExpired += result.amount();
            }
            log.info("[POINT_EXPIRE] chunk written: count={}, totalAmount={}",
                chunk.size(), totalExpired);
        };
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  보조 타입
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /** Processor → Writer로 넘기는 결과 객체 */
    public record ExpireResult(PointHistory expireHistory, int amount) {}
}
