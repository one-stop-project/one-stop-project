package com.sparta.one_stop.domain.point.service;

import com.sparta.one_stop.domain.point.entity.Point;
import com.sparta.one_stop.domain.point.entity.PointHistory;
import com.sparta.one_stop.domain.point.repository.PointHistoryRepository;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PointExpireService {

    private static final List<PointHistoryType> DEDUCTIBLE_TYPES = List.of(
        PointHistoryType.CHARGE,
        PointHistoryType.EARN,
        PointHistoryType.REFUND
    );

    private final PointHistoryRepository pointHistoryRepository;

    /**
     * 만료 대상 포인트 처리
     * - expireAt이 today 이전 또는 today인 포인트 이력 대상
     * - remainingAmount가 남아 있는 CHARGE / EARN / REFUND 이력만 처리
     * - 원본 이력의 remainingAmount를 0으로 변경
     * - 만료 금액만큼 Point balance 차감
     * - PointHistory EXPIRE 이력 저장
     */
    @Transactional
    public int expirePoints(LocalDate today) {
        List<PointHistory> expireTargets = pointHistoryRepository.findExpireTargetHistories(
            DEDUCTIBLE_TYPES,
            today
        );

        if (expireTargets.isEmpty()) {
            return 0;
        }

        int totalExpiredAmount = 0;
        List<PointHistory> expireHistories = new ArrayList<>();

        for (PointHistory targetHistory : expireTargets) {
            Integer expiredAmount = targetHistory.expireRemainingAmount();

            if (expiredAmount == 0) {
                continue;
            }

            Point point = targetHistory.getPoint();

            point.decreaseBalance(expiredAmount);

            PointHistory expireHistory = PointHistory.expire(
                point,
                expiredAmount,
                "포인트 만료"
            );

            expireHistories.add(expireHistory);
            totalExpiredAmount += expiredAmount;
        }

        pointHistoryRepository.saveAll(expireHistories);

        return totalExpiredAmount;
    }

}
