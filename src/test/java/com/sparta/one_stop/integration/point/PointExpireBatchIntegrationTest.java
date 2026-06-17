package com.sparta.one_stop.integration.point;

import com.sparta.one_stop.domain.point.entity.Point;
import com.sparta.one_stop.domain.point.entity.PointHistory;
import com.sparta.one_stop.domain.point.repository.PointHistoryRepository;
import com.sparta.one_stop.domain.point.repository.PointRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.integration.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PointExpireBatchIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier("pointExpireJob")
    private Job pointExpireJob;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private RedissonClient redissonClient;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(pointExpireJob);
    }

    @AfterEach
    void tearDown() {
        entityManager.clear();

        jobRepositoryTestUtils.removeJobExecutions();

        pointHistoryRepository.deleteAllInBatch();
        pointRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("pointExpireJob 성공 - expireDate 이전 만료 대상 포인트를 만료 처리한다")
    void pointExpireJob_success_expirePointHistoriesBeforeExpireDate() throws Exception {
        // given
        LocalDate expireDate = LocalDate.now();

        User user = createBuyer();
        Point point = createPointWithBalance(
            user,
            6_000
        );

        PointHistory expiredHistory = PointHistory.charge(
            point,
            1_000,
            "만료 대상 충전 포인트",
            expireDate.minusDays(1)
        );

        PointHistory todayHistory = PointHistory.charge(
            point,
            2_000,
            "오늘 만료 충전 포인트",
            expireDate
        );

        PointHistory futureHistory = PointHistory.charge(
            point,
            3_000,
            "미래 만료 충전 포인트",
            expireDate.plusDays(1)
        );

        pointHistoryRepository.saveAllAndFlush(List.of(
            expiredHistory,
            todayHistory,
            futureHistory
        ));

        Long pointId = point.getId();
        Long expiredHistoryId = expiredHistory.getId();
        Long todayHistoryId = todayHistory.getId();
        Long futureHistoryId = futureHistory.getId();

        entityManager.clear();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(
            jobParameters(expireDate)
        );

        entityManager.clear();

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Point refreshedPoint = pointRepository.findById(pointId)
            .orElseThrow();

        PointHistory refreshedExpiredHistory = pointHistoryRepository.findById(expiredHistoryId)
            .orElseThrow();

        PointHistory refreshedTodayHistory = pointHistoryRepository.findById(todayHistoryId)
            .orElseThrow();

        PointHistory refreshedFutureHistory = pointHistoryRepository.findById(futureHistoryId)
            .orElseThrow();

        assertThat(refreshedExpiredHistory.getRemainingAmount()).isZero();
        assertThat(refreshedTodayHistory.getRemainingAmount()).isEqualTo(2_000);
        assertThat(refreshedFutureHistory.getRemainingAmount()).isEqualTo(3_000);

        assertThat(refreshedPoint.getBalance()).isEqualTo(5_000);

        List<PointHistory> expireHistories = pointHistoryRepository.findAll()
            .stream()
            .filter(history -> history.getType() == PointHistoryType.EXPIRE)
            .toList();

        assertThat(expireHistories).hasSize(1);

        PointHistory expireHistory = expireHistories.get(0);

        assertThat(expireHistory.getPoint().getId()).isEqualTo(pointId);
        assertThat(expireHistory.getUser().getId()).isEqualTo(user.getId());
        assertThat(expireHistory.getAmount()).isEqualTo(-1_000);
        assertThat(expireHistory.getRemainingAmount()).isZero();
        assertThat(expireHistory.getExpireAt()).isNull();
    }

    @Test
    @DisplayName("pointExpireJob 성공 - expireDate 당일과 이후 만료 포인트는 만료 처리하지 않는다")
    void pointExpireJob_success_skipWhenExpireAtIsTodayOrFuture() throws Exception {
        // given
        LocalDate expireDate = LocalDate.now();

        User user = createBuyer();
        Point point = createPointWithBalance(
            user,
            5_000
        );

        PointHistory todayHistory = PointHistory.charge(
            point,
            2_000,
            "오늘 만료 충전 포인트",
            expireDate
        );

        PointHistory futureHistory = PointHistory.charge(
            point,
            3_000,
            "미래 만료 충전 포인트",
            expireDate.plusDays(1)
        );

        pointHistoryRepository.saveAllAndFlush(List.of(
            todayHistory,
            futureHistory
        ));

        Long pointId = point.getId();
        Long todayHistoryId = todayHistory.getId();
        Long futureHistoryId = futureHistory.getId();

        entityManager.clear();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(
            jobParameters(expireDate)
        );

        entityManager.clear();

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Point refreshedPoint = pointRepository.findById(pointId)
            .orElseThrow();

        PointHistory refreshedTodayHistory = pointHistoryRepository.findById(todayHistoryId)
            .orElseThrow();

        PointHistory refreshedFutureHistory = pointHistoryRepository.findById(futureHistoryId)
            .orElseThrow();

        assertThat(refreshedPoint.getBalance()).isEqualTo(5_000);
        assertThat(refreshedTodayHistory.getRemainingAmount()).isEqualTo(2_000);
        assertThat(refreshedFutureHistory.getRemainingAmount()).isEqualTo(3_000);

        List<PointHistory> expireHistories = pointHistoryRepository.findAll()
            .stream()
            .filter(history -> history.getType() == PointHistoryType.EXPIRE)
            .toList();

        assertThat(expireHistories).isEmpty();
    }

    @Test
    @DisplayName("pointExpireJob 성공 - remainingAmount가 0인 만료 대상은 EXPIRE 이력을 생성하지 않는다")
    void pointExpireJob_success_skipWhenRemainingAmountIsZero() throws Exception {
        // given
        LocalDate expireDate = LocalDate.now();

        User user = createBuyer();
        Point point = createPointWithBalance(
            user,
            1_000
        );

        PointHistory alreadyUsedHistory = PointHistory.charge(
            point,
            1_000,
            "이미 모두 사용된 충전 포인트",
            expireDate.minusDays(1)
        );

        alreadyUsedHistory.decreaseRemainingAmount(1_000);

        pointHistoryRepository.saveAndFlush(alreadyUsedHistory);

        Long pointId = point.getId();
        Long historyId = alreadyUsedHistory.getId();

        entityManager.clear();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(
            jobParameters(expireDate)
        );

        entityManager.clear();

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Point refreshedPoint = pointRepository.findById(pointId)
            .orElseThrow();

        PointHistory refreshedHistory = pointHistoryRepository.findById(historyId)
            .orElseThrow();

        assertThat(refreshedPoint.getBalance()).isEqualTo(1_000);
        assertThat(refreshedHistory.getRemainingAmount()).isZero();

        List<PointHistory> expireHistories = pointHistoryRepository.findAll()
            .stream()
            .filter(history -> history.getType() == PointHistoryType.EXPIRE)
            .toList();

        assertThat(expireHistories).isEmpty();
    }

    @Test
    @DisplayName("pointExpireJob 성공 - 여러 만료 대상 포인트를 chunk 단위로 처리한다")
    void pointExpireJob_success_processMultipleExpireTargets() throws Exception {
        // given
        LocalDate expireDate = LocalDate.now();

        User user = createBuyer();
        Point point = createPointWithBalance(
            user,
            6_000
        );

        PointHistory expiredHistory1 = PointHistory.charge(
            point,
            1_000,
            "만료 대상 충전 포인트 1",
            expireDate.minusDays(3)
        );

        PointHistory expiredHistory2 = PointHistory.charge(
            point,
            2_000,
            "만료 대상 충전 포인트 2",
            expireDate.minusDays(2)
        );

        PointHistory expiredHistory3 = PointHistory.charge(
            point,
            3_000,
            "만료 대상 충전 포인트 3",
            expireDate.minusDays(1)
        );

        pointHistoryRepository.saveAllAndFlush(List.of(
            expiredHistory1,
            expiredHistory2,
            expiredHistory3
        ));

        Long pointId = point.getId();

        entityManager.clear();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(
            jobParameters(expireDate)
        );

        entityManager.clear();

        // then
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Point refreshedPoint = pointRepository.findById(pointId)
            .orElseThrow();

        assertThat(refreshedPoint.getBalance()).isZero();

        List<PointHistory> expireHistories = pointHistoryRepository.findAll()
            .stream()
            .filter(history -> history.getType() == PointHistoryType.EXPIRE)
            .toList();

        assertThat(expireHistories).hasSize(3);
        assertThat(expireHistories)
            .extracting(PointHistory::getAmount)
            .containsExactlyInAnyOrder(
                -1_000,
                -2_000,
                -3_000
            );
    }

    private User createBuyer() {
        String suffix = UUID.randomUUID().toString();

        return userRepository.saveAndFlush(User.builder()
            .email("buyer-" + suffix + "@test.com")
            .password("password1!")
            .name("구매자")
            .phone("010-1234-5678")
            .address("서울시 강남구")
            .role(UserRole.BUYER)
            .build()
        );
    }

    private Point createPointWithBalance(
        User user,
        int balance
    ) {
        Point point = pointRepository.saveAndFlush(Point.createInitial(user));

        if (balance > 0) {
            point.increaseBalance(balance);
            point = pointRepository.saveAndFlush(point);
        }

        return point;
    }

    private JobParameters jobParameters(LocalDate expireDate) {
        return new JobParametersBuilder()
            .addString(
                "expireDate",
                expireDate.toString()
            )
            .addLong(
                "run.id",
                System.currentTimeMillis()
            )
            .toJobParameters();
    }

}
