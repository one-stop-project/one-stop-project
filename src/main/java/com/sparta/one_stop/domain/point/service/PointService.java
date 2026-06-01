package com.sparta.one_stop.domain.point.service;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.point.dto.request.PointChargeRequest;
import com.sparta.one_stop.domain.point.dto.response.ExpiringSoonPointResponse;
import com.sparta.one_stop.domain.point.dto.response.PointChargeResponse;
import com.sparta.one_stop.domain.point.dto.response.PointHistoryPageResponse;
import com.sparta.one_stop.domain.point.dto.response.PointHistoryResponse;
import com.sparta.one_stop.domain.point.dto.response.PointResponse;
import com.sparta.one_stop.domain.point.entity.Point;
import com.sparta.one_stop.domain.point.entity.PointHistory;
import com.sparta.one_stop.domain.point.entity.PointUsageDetail;
import com.sparta.one_stop.domain.point.repository.PointHistoryRepository;
import com.sparta.one_stop.domain.point.repository.PointRepository;
import com.sparta.one_stop.domain.point.repository.PointUsageDetailRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private static final int CHARGE_MIN_AMOUNT = 1_000;
    private static final int CHARGE_MAX_AMOUNT = 1_000_000;
    private static final int EXPIRING_SOON_DAYS = 30;

    // 차감/만료/만료 예정 조회 대상이 되는 포인트 이력 유형
    // CHARGE / EARN / REFUND 이력만 remainingAmount를 가진다
    private static final List<PointHistoryType> DEDUCTIBLE_TYPES = List.of(
        PointHistoryType.CHARGE,
        PointHistoryType.EARN,
        PointHistoryType.REFUND
    );

    private final PointRepository pointRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final PointUsageDetailRepository pointUsageDetailRepository;
    private final UserRepository userRepository;

    /**
     * 내 포인트 잔액 + 이력 조회
     * - Point 계정이 없으면 balance 0, 빈 이력 응답 반환
     * - type이 없으면 전체 이력 조회
     * - type이 있으면 해당 포인트 이력 유형만 필터링
     * - expiringSoon은 30일 이내 만료 예정 포인트 기준으로 계산
     */
    public PointResponse getMyPoints(
        Long userId,
        PointHistoryType type,
        Pageable pageable
    ) {
        Point point = pointRepository.findByUserId(userId)
            .orElse(null);

        // 아직 포인트 계정이 생성되지 않은 사용자는 빈 포인트 응답 반환
        if (point == null) {
            return new PointResponse(
                0,
                new ExpiringSoonPointResponse(
                    0,
                    null
                ),
                emptyHistory(pageable)
            );
        }

        // 포인트 이력 페이징 조회
        Page<PointHistory> historyPage = findHistoryPage(
            userId,
            type,
            pageable
        );

        PointHistoryPageResponse history = toHistoryPageResponse(historyPage);

        // 30일 이내 만료 예정 포인트 조회
        ExpiringSoonPointResponse expiringSoon = getExpiringSoonPoint(point);

        return new PointResponse(
            point.getBalance(),
            expiringSoon,
            history
        );
    }

    /**
     * 테스트용 포인트 충전
     * - 개발/테스트 편의를 위한 임시 API
     * - 충전 금액은 1,000원 이상 1,000,000원 이하
     * - Point 계정이 없으면 새로 생성
     * - Point balance 증가
     * - PointHistory CHARGE 이력 저장
     * - 충전 포인트 만료일은 충전일 기준 1년 후
     */
    @Transactional
    public PointChargeResponse chargePoint(
        Long userId,
        PointChargeRequest request
    ) {
        validateChargeAmount(request.amount());

        // 사용자 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));

        // 포인트 계정 조회, 없으면 생성
        Point point = pointRepository.findByUserId(userId)
            .orElseGet(() -> pointRepository.save(new Point(user)));

        Integer amount = request.amount();
        LocalDate expireAt = LocalDate.now().plusYears(1);

        // 포인트 잔액 증가
        point.increaseBalance(amount);

        // 충전 이력 생성
        PointHistory history = PointHistory.charge(
            point,
            amount,
            "테스트용 포인트 충전",
            expireAt
        );

        pointHistoryRepository.save(history);

        return new PointChargeResponse(
            point.getBalance(),
            amount,
            expireAt
        );
    }

    /**
     * 포인트 사용 / 차감
     * - 결제 승인 시 호출
     * - usedPoint가 0이면 차감하지 않음
     * - Point balance 기준으로 1차 잔액 검증
     * - FEFO + FIFO 기준으로 원본 포인트 이력 차감
     * - PointHistory USE 이력 생성
     * - PointUsageDetail에 어떤 원본 이력에서 얼마를 차감했는지 기록
     */
    @Transactional
    public void usePoint(
        Long userId,
        Order order,
        Integer usedPoint
    ) {
        validateUsePointAmount(usedPoint);

        if (usedPoint == 0) {
            return;
        }

        if (order == null) {
            throw new CustomException(ErrorCode.COMMON_001);
        }

        Point point = pointRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.POINT_001));

        validateEnoughBalance(
            point,
            usedPoint
        );

        PointHistory useHistory = PointHistory.use(
            point,
            order,
            usedPoint,
            "주문 결제 포인트 사용"
        );

        pointHistoryRepository.save(useHistory);

        deductPointHistories(
            point,
            useHistory,
            usedPoint
        );

        point.decreaseBalance(usedPoint);
    }

    /**
     * 테스트용 포인트 충전 금액 검증
     * - 최소 1,000원
     * - 최대 1,000,000원
     */
    private void validateChargeAmount(Integer amount) {
        if (amount == null
            || amount < CHARGE_MIN_AMOUNT
            || amount > CHARGE_MAX_AMOUNT) {
            throw new CustomException(ErrorCode.POINT_004);
        }
    }

    /**
     * 포인트 이력 페이징 조회
     * - type이 없으면 전체 이력 조회
     * - type이 있으면 해당 유형 이력만 조회
     */
    private Page<PointHistory> findHistoryPage(
        Long userId,
        PointHistoryType type,
        Pageable pageable
    ) {
        if (type == null) {
            return pointHistoryRepository.findAllByUserId(
                userId,
                pageable
            );
        }

        return pointHistoryRepository.findAllByUserIdAndType(
            userId,
            type,
            pageable
        );
    }

    /**
     * 30일 이내 만료 예정 포인트 조회
     * - remainingAmount가 남아 있는 CHARGE / EARN / REFUND 이력 기준
     * - amount는 30일 이내 만료 예정 포인트 합계
     * - expireAt은 가장 가까운 만료일
     */
    private ExpiringSoonPointResponse getExpiringSoonPoint(Point point) {
        LocalDate today = LocalDate.now();
        LocalDate until = today.plusDays(EXPIRING_SOON_DAYS);

        Long amount = pointHistoryRepository.sumExpiringSoonAmount(
            point.getId(),
            DEDUCTIBLE_TYPES,
            today,
            until
        );

        LocalDate expireAt = pointHistoryRepository.findNearestExpiringDate(
            point.getId(),
            DEDUCTIBLE_TYPES,
            today,
            until
        ).orElse(null);

        return new ExpiringSoonPointResponse(
            amount.intValue(),
            expireAt
        );
    }

    /**
     * PointHistory Page를 응답 DTO로 변환
     */
    private PointHistoryPageResponse toHistoryPageResponse(Page<PointHistory> historyPage) {
        List<PointHistoryResponse> content = historyPage.getContent()
            .stream()
            .map(this::toHistoryResponse)
            .toList();

        return new PointHistoryPageResponse(
            content,
            historyPage.getNumber(),
            historyPage.getSize(),
            historyPage.getTotalElements(),
            historyPage.getTotalPages()
        );
    }

    /**
     * PointHistory 단건을 응답 DTO로 변환
     */
    private PointHistoryResponse toHistoryResponse(PointHistory history) {
        return new PointHistoryResponse(
            history.getId(),
            history.getAmount(),
            history.getType(),
            history.getDescription(),
            history.getExpireAt(),
            history.getCreatedAt()
        );
    }

    /**
     * 포인트 계정이 없는 사용자용 빈 이력 응답 생성
     */
    private PointHistoryPageResponse emptyHistory(Pageable pageable) {
        return new PointHistoryPageResponse(
            List.of(),
            pageable.getPageNumber(),
            pageable.getPageSize(),
            0,
            0
        );
    }

    /**
     * 포인트 사용 금액 검증
     * - 주문 생성 시 0원 입력은 허용
     * - 실제 차감 메서드에서도 0원은 허용
     * - 음수 또는 null은 허용하지 않음
     */
    private void validateUsePointAmount(Integer usedPoint) {
        if (usedPoint == null || usedPoint < 0) {
            throw new CustomException(ErrorCode.POINT_003);
        }
    }

    /**
     * 포인트 잔액 검증
     */
    private void validateEnoughBalance(
        Point point,
        Integer usedPoint
    ) {
        if (point.getBalance() < usedPoint) {
            throw new CustomException(ErrorCode.POINT_002);
        }
    }

    /**
     * FEFO + FIFO 기준 포인트 원본 이력 차감
     * - CHARGE / EARN / REFUND 이력 중 remainingAmount가 남아 있고 만료되지 않은 이력만 대상
     * - Repository 조회 정렬 기준:
     *   1. expireAt ASC
     *   2. createdAt ASC
     * - PointUsageDetail 생성 후 sourceHistory.remainingAmount를 차감해야 함
     */
    private void deductPointHistories(
        Point point,
        PointHistory useHistory,
        Integer usedPoint
    ) {
        LocalDate today = LocalDate.now();

        List<PointHistory> sourceHistories =
            pointHistoryRepository.findDeductibleHistories(
                point.getId(),
                DEDUCTIBLE_TYPES,
                today
            );

        int remainingPoint = usedPoint;
        List<PointUsageDetail> usageDetails = new ArrayList<>();

        for (PointHistory sourceHistory : sourceHistories) {
            if (remainingPoint == 0) {
                break;
            }

            int usedAmount = Math.min(
                sourceHistory.getRemainingAmount(),
                remainingPoint
            );

            PointUsageDetail usageDetail = new PointUsageDetail(
                useHistory,
                sourceHistory,
                usedAmount
            );

            usageDetails.add(usageDetail);

            sourceHistory.decreaseRemainingAmount(usedAmount);

            remainingPoint -= usedAmount;
        }

        if (remainingPoint > 0) {
            throw new CustomException(ErrorCode.POINT_002);
        }

        pointUsageDetailRepository.saveAll(usageDetails);
    }

}
