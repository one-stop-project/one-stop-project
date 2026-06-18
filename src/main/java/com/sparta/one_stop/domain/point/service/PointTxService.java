package com.sparta.one_stop.domain.point.service;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointTxService {

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
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;

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

        // 잔액 응답 전에 무결성 검증 — DB 직접 조작 감지
        point.verifyIntegrity();

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
        // 신규 지갑 생성 시에는 첫 충전 동시 요청에서 user_id UNIQUE 제약 위반을
        // 메서드 내부에서 즉시 감지하여 재시도 대상 예외로 처리하기 위해 saveAndFlush를 사용한다.
        Point point = pointRepository.findByUserId(userId)
            .map(existing -> {
                existing.verifyIntegrity();
                return existing;
            })
            .orElseGet(() -> pointRepository.saveAndFlush(Point.createInitial(user)));

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

        Point point = getVerifiedPoint(userId);

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
     * 주문 취소 시 포인트 복구
     * - 주문 결제 시 생성된 USE 이력을 기준으로 복구
     * - 실제 차감 상세는 PointUsageDetail에서 조회
     * - 원래 차감된 포인트의 만료일을 유지하여 REFUND 이력 생성
     * - 원래 만료일이 이미 지난 포인트는 사용 가능 포인트로 복구하지 않음
     * - 복구된 포인트 총액을 반환
     * - USE 이력이 없으면 복구할 포인트가 없으므로 0 반환
     */
    @Transactional
    public Integer refundPointByOrder(Order order) {
        if (order == null) {
            throw new CustomException(ErrorCode.COMMON_001);
        }

        List<PointHistory> useHistories = pointHistoryRepository.findAllByOrderIdAndType(
            order.getId(),
            PointHistoryType.USE
        );

        if (useHistories.isEmpty()) {
            return 0;
        }

        List<Long> useHistoryIds = useHistories.stream()
            .map(PointHistory::getId)
            .toList();

        List<PointUsageDetail> usageDetails =
            pointUsageDetailRepository.findAllByUseHistoryIdIn(useHistoryIds);

        if (usageDetails.isEmpty()) {
            return 0;
        }

        Point point = useHistories.get(0)
            .getPoint();
        point.verifyIntegrity();

        LocalDate today = LocalDate.now();
        int restoredPoint = 0;
        List<PointHistory> refundHistories = new ArrayList<>();

        for (PointUsageDetail usageDetail : usageDetails) {
            LocalDate sourceExpireAt = usageDetail.getSourceExpireAt();

            // 원래 만료일이 이미 지난 포인트는 사용 가능 포인트로 복구하지 않음
            if (sourceExpireAt.isBefore(today)) {
                continue;
            }

            Integer refundAmount = usageDetail.getUsedAmount();

            PointHistory refundHistory = PointHistory.refund(
                point,
                order,
                refundAmount,
                "주문 취소 포인트 복구",
                sourceExpireAt
            );

            refundHistories.add(refundHistory);

            point.increaseBalance(refundAmount);

            restoredPoint += refundAmount;
        }

        pointHistoryRepository.saveAll(refundHistories);

        return restoredPoint;
    }

    /**
     * 배송 완료 후 포인트 적립
     * - 주문의 모든 OrderItem이 DELIVERED 상태일 때만 적립 (주문 단위 1회)
     * - 이미 해당 주문에 EARN 이력이 있으면 중복 적립 방지
     * - 적립 기준 금액 = 상품금액 - 쿠폰할인 - 사용포인트 - 구독할인 (배송비 제외)
     * - 적립 포인트 = floor(적립 기준 금액 * 0.01)
     * - 적립 포인트 만료일 = 적립일 기준 1년 후
     */
    @Transactional
    public void earnPointByDelivery(Long orderId) {

        // 주문의 모든 OrderItem이 DELIVERED 상태인지 확인
        // 일부만 배송 완료된 경우 포인트 적립하지 않음
        if (!orderItemRepository.isAllDelivered(orderId)) {
            return;
        }

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CustomException(ErrorCode.ORDER_006));

        // 중복 적립 방지 - 이미 해당 주문에 EARN 이력이 있으면 스킵
        List<PointHistory> existingEarn = pointHistoryRepository.findAllByOrderIdAndType(
            orderId,
            PointHistoryType.EARN
        );

        if (!existingEarn.isEmpty()) {
            log.warn("[POINT_EARN] 중복 적립 시도 감지 — orderId={}", orderId);
            return;
        }

        Long userId = order.getUser().getId();

        // Point 계정 없으면 새로 생성
        // 배송 완료 적립 경로는 첫 충전 재시도 목적의 즉시 flush가 필요하지 않으므로 save를 사용한다.
        Point point = pointRepository.findByUserId(userId)
            .map(existing -> {
                existing.verifyIntegrity();
                return existing;
            })
            .orElseGet(() -> {
                User user = order.getUser();
                return pointRepository.save(Point.createInitial(user));
            });

        // 적립 기준 금액 = 상품금액 - 쿠폰할인 - 사용포인트 - 구독할인 (배송비 제외)
        long baseAmount = order.getTotalPrice()
            - order.getDiscountPrice()
            - order.getUsedPoint()
            - order.getSubscriptionDiscount();

        if (baseAmount <= 0) {
            log.info("[POINT_EARN] 적립 기준 금액이 0 이하 — orderId={}, baseAmount={}", orderId, baseAmount);
            return;
        }

        // 적립 포인트 = floor(적립 기준 금액 * 0.01)
        int earnAmount = (int) Math.floor(baseAmount * 0.01);

        if (earnAmount <= 0) {
            log.info("[POINT_EARN] 적립 포인트가 0 이하 — orderId={}, earnAmount={}", orderId, earnAmount);
            return;
        }

        LocalDate expireAt = LocalDate.now().plusYears(1);

        PointHistory earnHistory = PointHistory.earn(
            point,
            order,
            earnAmount,
            String.format("주문 #%d 배송 완료 적립 (1%%)", orderId),
            expireAt
        );

        pointHistoryRepository.save(earnHistory);
        point.increaseBalance(earnAmount);

        log.info("[POINT_EARN] 포인트 적립 완료 — orderId={}, userId={}, earnAmount={}",
            orderId, userId, earnAmount);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  내부 헬퍼 메서드 (기존 PointService에서 그대로 이동)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━


    /**
     * 기존 포인트 계정을 조회하고 무결성을 검증한다.
     * 포인트 계정이 반드시 존재해야 하는 조회/차감 경로에서 사용한다.
     */
    private Point getVerifiedPoint(Long userId) {
        Point point = pointRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.POINT_001));

        point.verifyIntegrity();
        return point;
    }

    private void validateChargeAmount(Integer amount) {
        if (amount == null
            || amount < CHARGE_MIN_AMOUNT
            || amount > CHARGE_MAX_AMOUNT) {
            throw new CustomException(ErrorCode.POINT_004);
        }
    }

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

    private PointHistoryPageResponse emptyHistory(Pageable pageable) {
        return new PointHistoryPageResponse(
            List.of(),
            pageable.getPageNumber(),
            pageable.getPageSize(),
            0,
            0
        );
    }

    private void validateUsePointAmount(Integer usedPoint) {
        if (usedPoint == null || usedPoint < 0) {
            throw new CustomException(ErrorCode.POINT_003);
        }
    }

    private void validateEnoughBalance(
        Point point,
        Integer usedPoint
    ) {
        if (point.getBalance() < usedPoint) {
            throw new CustomException(ErrorCode.POINT_002);
        }
    }

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
