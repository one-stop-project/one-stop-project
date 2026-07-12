package com.sparta.one_stop.domain.point.admin;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.sparta.one_stop.domain.point.entity.QPoint;
import com.sparta.one_stop.domain.point.entity.QPointHistory;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin 포인트 운영/감사 서비스
 *
 * 제공 기능
 *
 *  전체 발행량 통계 — 총 적립/사용/만료/환불 합계
 *  정합성 검증 — points.balance vs SUM(point_history.remaining_amount)
 *  의심 사용자 검출 — 정합성 깨진 유저 리스트
 *  대시보드 지표 — 일/주/월 단위 추이
 *
 *
 * 타입 처리 주의사항
 *
 *   Point.balance / PointHistory.amount 는 Integer (단일 행)
 *   SUM 결과는 누적되면 Integer.MAX_VALUE(약 21억) 초과 가능 → Long으로 안전 변환
 *   coalesce(0)는 Integer 타입과 일치해야 함 (0L 불가)
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointAdminService {

    private final JPAQueryFactory queryFactory;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  1. 전체 발행량 통계
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 시스템 전체 포인트 발행/소비 통계
     *
     * 회계·재무 보고 시 사용
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PointSystemStats getSystemStats() {
        QPointHistory ph = QPointHistory.pointHistory;
        QPoint p = QPoint.point;

        // ★ SUM 결과 표현식을 변수로 빼서 select와 t.get()에서 동일하게 사용
        //   - amount(Integer).sum() → NumberExpression<Integer>
        //   - coalesce(0) → Integer 타입과 일치 (0L 쓰면 컴파일 에러)
        //   - 누적합이 21억 초과 가능하므로 .longValue()로 Long 변환 (오버플로우 방어)
        NumberExpression<Long> amountSum = ph.amount.sum().coalesce(0).longValue();

        // 타입별 합계 (amount 기준 — 발생한 모든 변동의 누적)
        List<Tuple> typeStats = queryFactory
            .select(ph.type, amountSum)
            .from(ph)
            .groupBy(ph.type)
            .fetch();

        long totalCharged = 0, totalEarned = 0, totalUsed = 0, totalRefunded = 0, totalExpired = 0;

        for (Tuple t : typeStats) {
            PointHistoryType type = t.get(ph.type);
            Long sum = t.get(amountSum);
            if (sum == null || type == null) continue;

            switch (type) {
                case CHARGE  -> totalCharged = sum;
                case EARN    -> totalEarned = sum;
                case USE     -> totalUsed = Math.abs(sum);       // USE는 음수 저장
                case REFUND  -> totalRefunded = sum;
                case EXPIRE  -> totalExpired = Math.abs(sum);    // EXPIRE도 음수 저장
            }
        }

        // 현재 시스템에 살아있는 총 잔액 (모든 유저 balance 합)
        // ★ balance(Integer).sum() → coalesce(0) Integer 일치 → longValue()로 안전 변환
        Long totalLiveBalance = queryFactory
            .select(p.balance.sum().coalesce(0).longValue())
            .from(p)
            .fetchOne();

        // 회계 항등식 검증
        // 발행(CHARGE+EARN+REFUND) - 소멸(USE+EXPIRE) = 잔액 합
        long issued = totalCharged + totalEarned + totalRefunded;
        long consumed = totalUsed + totalExpired;
        long expectedBalance = issued - consumed;
        long balanceDiff = expectedBalance - (totalLiveBalance == null ? 0 : totalLiveBalance);

        return PointSystemStats.builder()
            .totalCharged(totalCharged)
            .totalEarned(totalEarned)
            .totalUsed(totalUsed)
            .totalRefunded(totalRefunded)
            .totalExpired(totalExpired)
            .totalLiveBalance(totalLiveBalance == null ? 0 : totalLiveBalance)
            .accountingDiff(balanceDiff)
            .isConsistent(balanceDiff == 0)
            .build();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  2. 정합성 검증 — 의심 유저 검출
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * 모든 사용자에 대해 balance vs SUM(remaining) 정합성 검증
     *
     * 주의: 대용량 운영에서는 페이징 + 배치로 처리 권장
     */
    public List<PointInconsistencyReport> findInconsistentUsers(int limit) {
        QPoint p = QPoint.point;
        QPointHistory ph = QPointHistory.pointHistory;

        // ★ 동일 표현식 변수화 — select / having / t.get() 에서 같은 인스턴스 사용
        //   remainingAmount(Integer).sum().coalesce(0) → NumberExpression<Integer>
        NumberExpression<Integer> remainingSumExpr = ph.remainingAmount.sum().coalesce(0);

        // 각 유저의 balance vs SUM(remaining)
        List<Tuple> all = queryFactory
            .select(
                p.user.id,
                p.balance,
                remainingSumExpr
            )
            .from(p)
            .leftJoin(ph).on(ph.point.eq(p)
                .and(ph.remainingAmount.gt(0)))
            .groupBy(p.user.id, p.balance)
            .having(p.balance.ne(remainingSumExpr))
            .limit(limit)
            .fetch();

        List<PointInconsistencyReport> reports = new ArrayList<>();
        for (Tuple t : all) {
            Long userId = t.get(p.user.id);
            Integer balance = t.get(p.balance);
            Integer remainingSum = t.get(remainingSumExpr);

            reports.add(PointInconsistencyReport.builder()
                .userId(userId)
                .balance(balance)
                .remainingAmountSum(remainingSum)
                .diff(balance - (remainingSum == null ? 0 : remainingSum))
                .build());
        }

        if (!reports.isEmpty()) {
            log.warn("[POINT_AUDIT] 정합성 불일치 사용자 검출 — count={}", reports.size());
        }
        return reports;
    }
}
