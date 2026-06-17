package com.sparta.one_stop.domain.point.service;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.point.dto.request.PointChargeRequest;
import com.sparta.one_stop.domain.point.dto.response.PointChargeResponse;
import com.sparta.one_stop.domain.point.entity.Point;
import com.sparta.one_stop.domain.point.entity.PointHistory;
import com.sparta.one_stop.domain.point.entity.PointUsageDetail;
import com.sparta.one_stop.domain.point.repository.PointHistoryRepository;
import com.sparta.one_stop.domain.point.repository.PointRepository;
import com.sparta.one_stop.domain.point.repository.PointUsageDetailRepository;
import com.sparta.one_stop.domain.point.util.PointIntegrityHasher;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointTxServiceTest {

    @Mock
    private PointRepository pointRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Mock
    private PointUsageDetailRepository pointUsageDetailRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private PointTxService pointTxService;

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

    private static Order mockOrder() {
        return mock(Order.class);
    }

    private static Order mockOrder(Long orderId) {
        Order order = mock(Order.class);

        when(order.getId()).thenReturn(orderId);

        return order;
    }

    private static Point createPointWithBalance(
        User user,
        int balance
    ) {
        Point point = Point.createInitial(user);

        if (balance > 0) {
            point.increaseBalance(balance);

            // increaseBalance()는 flush 후 version+1을 기준으로 무결성 해시를 만든다.
            // 단위 테스트에는 JPA flush가 없으므로 영속화 이후 상태를 직접 재현한다.
            setField(point, "version", 1);
        }

        return point;
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

    private static void setField(
        Object target,
        String fieldName,
        Object value
    ) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);

            field.setAccessible(true);
            field.set(
                target,
                value
            );
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("getMyPoints 성공 - 포인트 계정이 없으면 빈 응답을 반환한다")
    void getMyPoints_success_whenPointDoesNotExist() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.empty());

        // when
        var response = pointTxService.getMyPoints(
            userId,
            null,
            pageable
        );

        // then
        assertThat(response.balance()).isZero();
        assertThat(response.expiringSoon().amount()).isZero();
        assertThat(response.expiringSoon().expireAt()).isNull();

        assertThat(response.history().content()).isEmpty();
        assertThat(response.history().page()).isEqualTo(0);
        assertThat(response.history().size()).isEqualTo(10);
        assertThat(response.history().totalElements()).isZero();
        assertThat(response.history().totalPages()).isZero();

        verify(pointHistoryRepository, never()).findAllByUserId(anyLong(), any());
        verify(pointHistoryRepository, never()).findAllByUserIdAndType(anyLong(), any(), any());
        verify(pointHistoryRepository, never()).sumExpiringSoonAmount(any(), any(), any(), any());
        verify(pointHistoryRepository, never()).findNearestExpiringDate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("getMyPoints 성공 - type이 null이면 전체 포인트 이력을 조회한다")
    void getMyPoints_success_findAllHistory_whenTypeIsNull() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        User user = mockUser(userId);
        Point point = createPointWithBalance(
            user,
            5000
        );
        setField(point, "id", 100L);

        PointHistory history = PointHistory.charge(
            point,
            5000,
            "테스트 충전",
            LocalDate.now().plusYears(1)
        );

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        when(pointHistoryRepository.findAllByUserId(
            userId,
            pageable
        )).thenReturn(new PageImpl<>(
            List.of(history),
            pageable,
            1
        ));

        when(pointHistoryRepository.sumExpiringSoonAmount(
            any(),
            any(),
            any(),
            any()
        )).thenReturn(3000L);

        when(pointHistoryRepository.findNearestExpiringDate(
            any(),
            any(),
            any(),
            any()
        )).thenReturn(Optional.of(LocalDate.now().plusDays(7)));

        // when
        var response = pointTxService.getMyPoints(
            userId,
            null,
            pageable
        );

        // then
        assertThat(response.balance()).isEqualTo(5000);

        assertThat(response.history().content()).hasSize(1);
        assertThat(response.history().page()).isEqualTo(0);
        assertThat(response.history().size()).isEqualTo(10);
        assertThat(response.history().totalElements()).isEqualTo(1);
        assertThat(response.history().totalPages()).isEqualTo(1);

        assertThat(response.expiringSoon().amount()).isEqualTo(3000);
        assertThat(response.expiringSoon().expireAt()).isEqualTo(LocalDate.now().plusDays(7));

        verify(pointHistoryRepository).findAllByUserId(
            userId,
            pageable
        );
        verify(pointHistoryRepository, never()).findAllByUserIdAndType(anyLong(), any(), any());
    }

    @Test
    @DisplayName("getMyPoints 성공 - type이 있으면 해당 타입 이력만 조회한다")
    void getMyPoints_success_findHistoryByType_whenTypeExists() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        PointHistoryType type = PointHistoryType.CHARGE;

        User user = mockUser(userId);
        Point point = createPointWithBalance(
            user,
            5000
        );
        setField(point, "id", 100L);

        PointHistory history = PointHistory.charge(
            point,
            5000,
            "테스트 충전",
            LocalDate.now().plusYears(1)
        );

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        when(pointHistoryRepository.findAllByUserIdAndType(
            userId,
            type,
            pageable
        )).thenReturn(new PageImpl<>(
            List.of(history),
            pageable,
            1
        ));

        when(pointHistoryRepository.sumExpiringSoonAmount(
            any(),
            any(),
            any(),
            any()
        )).thenReturn(0L);

        when(pointHistoryRepository.findNearestExpiringDate(
            any(),
            any(),
            any(),
            any()
        )).thenReturn(Optional.empty());

        // when
        var response = pointTxService.getMyPoints(
            userId,
            type,
            pageable
        );

        // then
        assertThat(response.balance()).isEqualTo(5000);
        assertThat(response.history().content()).hasSize(1);
        assertThat(response.history().content().get(0).type()).isEqualTo(PointHistoryType.CHARGE);

        assertThat(response.expiringSoon().amount()).isZero();
        assertThat(response.expiringSoon().expireAt()).isNull();

        verify(pointHistoryRepository, never()).findAllByUserId(anyLong(), any());
        verify(pointHistoryRepository).findAllByUserIdAndType(
            userId,
            type,
            pageable
        );
    }

    @Test
    @DisplayName("chargePoint 성공 - 포인트 계정이 있으면 balance 증가 + CHARGE 이력 저장")
    void chargePoint_success_whenPointExists() {
        // given
        Long userId = 1L;
        User user = mockUser(userId);
        Point point = createPointWithBalance(
            user,
            1000
        );
        PointChargeRequest request = new PointChargeRequest(5000);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        // when
        PointChargeResponse response = pointTxService.chargePoint(
            userId,
            request
        );

        // then
        assertThat(response).isNotNull();
        assertThat(point.getBalance()).isEqualTo(6000);

        ArgumentCaptor<PointHistory> historyCaptor =
            ArgumentCaptor.forClass(PointHistory.class);

        verify(pointHistoryRepository).save(historyCaptor.capture());

        PointHistory savedHistory = historyCaptor.getValue();

        assertThat(savedHistory.getPoint()).isSameAs(point);
        assertThat(savedHistory.getUser()).isSameAs(user);
        assertThat(savedHistory.getAmount()).isEqualTo(5000);
        assertThat(savedHistory.getRemainingAmount()).isEqualTo(5000);
        assertThat(savedHistory.getType()).isEqualTo(PointHistoryType.CHARGE);
        assertThat(savedHistory.getDescription()).isEqualTo("테스트용 포인트 충전");
        assertThat(savedHistory.getExpireAt()).isEqualTo(LocalDate.now().plusYears(1));

        verify(pointRepository, never()).save(any(Point.class));
        verify(pointRepository, never()).saveAndFlush(any(Point.class));
    }

    @Test
    @DisplayName("chargePoint 성공 - 포인트 계정이 없으면 새로 생성 후 충전")
    void chargePoint_success_whenPointDoesNotExist() {
        // given
        Long userId = 1L;
        User user = mockUser(userId);
        PointChargeRequest request = new PointChargeRequest(5000);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.empty());
        when(pointRepository.saveAndFlush(any(Point.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        PointChargeResponse response = pointTxService.chargePoint(
            userId,
            request
        );

        // then
        assertThat(response).isNotNull();

        ArgumentCaptor<Point> pointCaptor =
            ArgumentCaptor.forClass(Point.class);

        verify(pointRepository).saveAndFlush(pointCaptor.capture());
        verify(pointRepository, never()).save(any(Point.class));

        Point savedPoint = pointCaptor.getValue();

        assertThat(savedPoint.getUser()).isSameAs(user);
        assertThat(savedPoint.getBalance()).isEqualTo(5000);

        verify(pointHistoryRepository).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("chargePoint 실패 - 충전 금액이 1,000원 미만이면 예외가 발생한다")
    void chargePoint_fail_whenAmountIsLessThanMinAmount() {
        // given
        PointChargeRequest request = new PointChargeRequest(999);

        // when & then
        assertThatThrownBy(() -> pointTxService.chargePoint(
            1L,
            request
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_004);
            });

        verify(userRepository, never()).findById(anyLong());
        verify(pointRepository, never()).findByUserId(anyLong());
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("chargePoint 실패 - 충전 금액이 1,000,000원 초과이면 예외가 발생한다")
    void chargePoint_fail_whenAmountIsGreaterThanMaxAmount() {
        // given
        PointChargeRequest request = new PointChargeRequest(1_000_001);

        // when & then
        assertThatThrownBy(() -> pointTxService.chargePoint(
            1L,
            request
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_004);
            });

        verify(userRepository, never()).findById(anyLong());
        verify(pointRepository, never()).findByUserId(anyLong());
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("chargePoint 실패 - 사용자가 존재하지 않으면 예외가 발생한다")
    void chargePoint_fail_whenUserDoesNotExist() {
        // given
        Long userId = 1L;
        PointChargeRequest request = new PointChargeRequest(5000);

        when(userRepository.findById(userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pointTxService.chargePoint(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.MEMBER_001);
            });

        verify(pointRepository, never()).findByUserId(userId);
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("usePoint 성공 - 포인트 차감 + USE 이력 + PointUsageDetail 생성")
    void usePoint_success() {
        // given
        Long userId = 1L;
        User user = mockUser(userId);
        Point point = createPointWithBalance(
            user,
            10000
        );
        Order order = mockOrder();

        PointHistory sourceHistory1 = PointHistory.charge(
            point,
            3000,
            "테스트 충전 1",
            LocalDate.now().plusMonths(3)
        );

        PointHistory sourceHistory2 = PointHistory.charge(
            point,
            7000,
            "테스트 충전 2",
            LocalDate.now().plusMonths(6)
        );

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        when(pointHistoryRepository.findDeductibleHistories(
            any(),
            any(),
            any()
        ))
            .thenReturn(List.of(
                sourceHistory1,
                sourceHistory2
            ));

        // when
        pointTxService.usePoint(
            userId,
            order,
            5000
        );

        // then
        assertThat(point.getBalance()).isEqualTo(5000);
        assertThat(sourceHistory1.getRemainingAmount()).isEqualTo(0);
        assertThat(sourceHistory2.getRemainingAmount()).isEqualTo(5000);

        ArgumentCaptor<PointHistory> useHistoryCaptor =
            ArgumentCaptor.forClass(PointHistory.class);

        verify(pointHistoryRepository).save(useHistoryCaptor.capture());

        PointHistory useHistory = useHistoryCaptor.getValue();

        assertThat(useHistory.getPoint()).isSameAs(point);
        assertThat(useHistory.getUser()).isSameAs(user);
        assertThat(useHistory.getOrder()).isSameAs(order);
        assertThat(useHistory.getAmount()).isEqualTo(-5000);
        assertThat(useHistory.getRemainingAmount()).isEqualTo(0);
        assertThat(useHistory.getType()).isEqualTo(PointHistoryType.USE);

        ArgumentCaptor<List> usageDetailCaptor =
            ArgumentCaptor.forClass(List.class);

        verify(pointUsageDetailRepository).saveAll(usageDetailCaptor.capture());

        List<PointUsageDetail> usageDetails = usageDetailCaptor.getValue();

        assertThat(usageDetails).hasSize(2);
        assertThat(usageDetails.get(0).getUseHistory()).isSameAs(useHistory);
        assertThat(usageDetails.get(0).getSourceHistory()).isSameAs(sourceHistory1);
        assertThat(usageDetails.get(0).getUsedAmount()).isEqualTo(3000);
        assertThat(usageDetails.get(1).getUseHistory()).isSameAs(useHistory);
        assertThat(usageDetails.get(1).getSourceHistory()).isSameAs(sourceHistory2);
        assertThat(usageDetails.get(1).getUsedAmount()).isEqualTo(2000);
    }

    @Test
    @DisplayName("usePoint 성공 - usedPoint가 0이면 차감하지 않는다")
    void usePoint_success_whenUsedPointIsZero() {
        // given
        Order order = mockOrder();

        // when
        pointTxService.usePoint(
            1L,
            order,
            0
        );

        // then
        verify(pointRepository, never()).findByUserId(anyLong());
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
        verify(pointUsageDetailRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("usePoint 실패 - 포인트 계정이 없으면 예외가 발생한다")
    void usePoint_fail_whenPointDoesNotExist() {
        // given
        Long userId = 1L;
        Order order = mockOrder();

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> pointTxService.usePoint(
            userId,
            order,
            1000
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_001);
            });

        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
        verify(pointUsageDetailRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("usePoint 실패 - 잔액이 부족하면 예외가 발생한다")
    void usePoint_fail_whenBalanceIsNotEnough() {
        // given
        Long userId = 1L;
        User user = mockUser(userId);
        Point point = createPointWithBalance(
            user,
            1000
        );
        Order order = mockOrder();

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        // when & then
        assertThatThrownBy(() -> pointTxService.usePoint(
            userId,
            order,
            1001
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_002);
            });

        assertThat(point.getBalance()).isEqualTo(1000);

        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
        verify(pointUsageDetailRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("usePoint 실패 - usedPoint가 음수이면 예외가 발생한다")
    void usePoint_fail_whenUsedPointIsNegative() {
        // given
        Order order = mockOrder();

        // when & then
        assertThatThrownBy(() -> pointTxService.usePoint(
            1L,
            order,
            -1
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_003);
            });

        verify(pointRepository, never()).findByUserId(anyLong());
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("usePoint 실패 - order가 null이면 예외가 발생한다")
    void usePoint_fail_whenOrderIsNull() {
        // when & then
        assertThatThrownBy(() -> pointTxService.usePoint(
            1L,
            null,
            1000
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_001);
            });

        verify(pointRepository, never()).findByUserId(anyLong());
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("refundPointByOrder 성공 - USE 이력 기준으로 포인트를 복구한다")
    void refundPointByOrder_success() {
        // given
        User user = mockUser(1L);
        Point point = createPointWithBalance(
            user,
            0
        );
        Order order = mockOrder(1L);

        PointHistory useHistory = PointHistory.use(
            point,
            order,
            3000,
            "주문 결제 포인트 사용"
        );
        setField(
            useHistory,
            "id",
            10L
        );

        PointHistory sourceHistory1 = PointHistory.charge(
            point,
            1000,
            "테스트 충전 1",
            LocalDate.now().plusMonths(3)
        );

        PointHistory sourceHistory2 = PointHistory.charge(
            point,
            2000,
            "테스트 충전 2",
            LocalDate.now().plusMonths(6)
        );

        PointUsageDetail usageDetail1 = new PointUsageDetail(
            useHistory,
            sourceHistory1,
            1000
        );

        PointUsageDetail usageDetail2 = new PointUsageDetail(
            useHistory,
            sourceHistory2,
            2000
        );

        when(pointHistoryRepository.findAllByOrderIdAndType(
            1L,
            PointHistoryType.USE
        ))
            .thenReturn(List.of(useHistory));

        when(pointUsageDetailRepository.findAllByUseHistoryIdIn(List.of(10L)))
            .thenReturn(List.of(
                usageDetail1,
                usageDetail2
            ));

        // when
        Integer restoredPoint = pointTxService.refundPointByOrder(order);

        // then
        assertThat(restoredPoint).isEqualTo(3000);
        assertThat(point.getBalance()).isEqualTo(3000);

        ArgumentCaptor<List> refundHistoriesCaptor =
            ArgumentCaptor.forClass(List.class);

        verify(pointHistoryRepository).saveAll(refundHistoriesCaptor.capture());

        List<PointHistory> refundHistories = refundHistoriesCaptor.getValue();

        assertThat(refundHistories).hasSize(2);
        assertThat(refundHistories.get(0).getType()).isEqualTo(PointHistoryType.REFUND);
        assertThat(refundHistories.get(0).getAmount()).isEqualTo(1000);
        assertThat(refundHistories.get(0).getRemainingAmount()).isEqualTo(1000);
        assertThat(refundHistories.get(1).getType()).isEqualTo(PointHistoryType.REFUND);
        assertThat(refundHistories.get(1).getAmount()).isEqualTo(2000);
        assertThat(refundHistories.get(1).getRemainingAmount()).isEqualTo(2000);
    }

    @Test
    @DisplayName("refundPointByOrder 성공 - 만료된 포인트는 복구하지 않는다")
    void refundPointByOrder_success_skipExpiredPoint() {
        // given
        User user = mockUser(1L);
        Point point = createPointWithBalance(
            user,
            0
        );
        Order order = mockOrder(1L);

        PointHistory useHistory = PointHistory.use(
            point,
            order,
            1000,
            "주문 결제 포인트 사용"
        );
        setField(
            useHistory,
            "id",
            10L
        );

        PointHistory expiredSourceHistory = PointHistory.charge(
            point,
            1000,
            "만료된 충전 포인트",
            LocalDate.now().minusDays(1)
        );

        PointUsageDetail usageDetail = new PointUsageDetail(
            useHistory,
            expiredSourceHistory,
            1000
        );

        when(pointHistoryRepository.findAllByOrderIdAndType(
            1L,
            PointHistoryType.USE
        ))
            .thenReturn(List.of(useHistory));

        when(pointUsageDetailRepository.findAllByUseHistoryIdIn(List.of(10L)))
            .thenReturn(List.of(usageDetail));

        // when
        Integer restoredPoint = pointTxService.refundPointByOrder(order);

        // then
        assertThat(restoredPoint).isEqualTo(0);
        assertThat(point.getBalance()).isEqualTo(0);

        ArgumentCaptor<List> refundHistoriesCaptor =
            ArgumentCaptor.forClass(List.class);

        verify(pointHistoryRepository).saveAll(refundHistoriesCaptor.capture());

        assertThat(refundHistoriesCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("refundPointByOrder 성공 - USE 이력이 없으면 0을 반환한다")
    void refundPointByOrder_success_whenUseHistoryDoesNotExist() {
        // given
        Order order = mockOrder(1L);

        when(pointHistoryRepository.findAllByOrderIdAndType(
            1L,
            PointHistoryType.USE
        ))
            .thenReturn(List.of());

        // when
        Integer restoredPoint = pointTxService.refundPointByOrder(order);

        // then
        assertThat(restoredPoint).isEqualTo(0);

        verify(pointUsageDetailRepository, never()).findAllByUseHistoryIdIn(any());
        verify(pointHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("refundPointByOrder 성공 - 사용 상세 이력이 없으면 0을 반환한다")
    void refundPointByOrder_success_whenUsageDetailDoesNotExist() {
        // given
        User user = mockUser(1L);
        Point point = createPointWithBalance(
            user,
            0
        );
        Order order = mockOrder(1L);

        PointHistory useHistory = PointHistory.use(
            point,
            order,
            1000,
            "주문 결제 포인트 사용"
        );
        setField(
            useHistory,
            "id",
            10L
        );

        when(pointHistoryRepository.findAllByOrderIdAndType(
            1L,
            PointHistoryType.USE
        ))
            .thenReturn(List.of(useHistory));

        when(pointUsageDetailRepository.findAllByUseHistoryIdIn(List.of(10L)))
            .thenReturn(List.of());

        // when
        Integer restoredPoint = pointTxService.refundPointByOrder(order);

        // then
        assertThat(restoredPoint).isEqualTo(0);

        verify(pointHistoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("refundPointByOrder 실패 - order가 null이면 예외가 발생한다")
    void refundPointByOrder_fail_whenOrderIsNull() {
        // when & then
        assertThatThrownBy(() -> pointTxService.refundPointByOrder(null))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.COMMON_001);
            });

        verify(pointHistoryRepository, never()).findAllByOrderIdAndType(
            any(),
            any()
        );
    }

    @Test
    @DisplayName("earnPointByDelivery 성공 - 모든 주문 상품이 배송 완료가 아니면 적립하지 않는다")
    void earnPointByDelivery_success_skip_whenNotAllOrderItemsDelivered() {
        // given
        Long orderId = 1L;

        when(orderItemRepository.isAllDelivered(orderId))
            .thenReturn(false);

        // when
        pointTxService.earnPointByDelivery(orderId);

        // then
        verify(orderRepository, never()).findById(anyLong());
        verify(pointHistoryRepository, never()).findAllByOrderIdAndType(anyLong(), any());
        verify(pointRepository, never()).findByUserId(anyLong());
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("earnPointByDelivery 성공 - 이미 EARN 이력이 있으면 중복 적립하지 않는다")
    void earnPointByDelivery_success_skip_whenEarnHistoryAlreadyExists() {
        // given
        Long orderId = 1L;
        Order order = mock(Order.class);
        PointHistory existingEarnHistory = mock(PointHistory.class);

        when(orderItemRepository.isAllDelivered(orderId))
            .thenReturn(true);

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        when(pointHistoryRepository.findAllByOrderIdAndType(
            orderId,
            PointHistoryType.EARN
        )).thenReturn(List.of(existingEarnHistory));

        // when
        pointTxService.earnPointByDelivery(orderId);

        // then
        verify(pointRepository, never()).findByUserId(anyLong());
        verify(pointRepository, never()).save(any(Point.class));
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("earnPointByDelivery 성공 - Point 계정이 없으면 자동 생성 후 적립한다")
    void earnPointByDelivery_success_createPoint_whenPointDoesNotExist() {
        // given
        Long orderId = 1L;
        Long userId = 1L;

        User user = mockUser(userId);
        Order order = mock(Order.class);

        when(order.getUser()).thenReturn(user);
        when(order.getTotalPrice()).thenReturn(10_000L);
        when(order.getDiscountPrice()).thenReturn(1_000L);
        when(order.getUsedPoint()).thenReturn(0);

        when(orderItemRepository.isAllDelivered(orderId))
            .thenReturn(true);

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        when(pointHistoryRepository.findAllByOrderIdAndType(
            orderId,
            PointHistoryType.EARN
        )).thenReturn(List.of());

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.empty());

        when(pointRepository.save(any(Point.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        pointTxService.earnPointByDelivery(orderId);

        // then
        ArgumentCaptor<Point> pointCaptor =
            ArgumentCaptor.forClass(Point.class);

        verify(pointRepository).save(pointCaptor.capture());

        Point savedPoint = pointCaptor.getValue();

        assertThat(savedPoint.getUser()).isSameAs(user);
        assertThat(savedPoint.getBalance()).isEqualTo(90);

        ArgumentCaptor<PointHistory> historyCaptor =
            ArgumentCaptor.forClass(PointHistory.class);

        verify(pointHistoryRepository).save(historyCaptor.capture());

        PointHistory savedHistory = historyCaptor.getValue();

        assertThat(savedHistory.getPoint()).isSameAs(savedPoint);
        assertThat(savedHistory.getUser()).isSameAs(user);
        assertThat(savedHistory.getOrder()).isSameAs(order);
        assertThat(savedHistory.getAmount()).isEqualTo(90);
        assertThat(savedHistory.getRemainingAmount()).isEqualTo(90);
        assertThat(savedHistory.getType()).isEqualTo(PointHistoryType.EARN);
        assertThat(savedHistory.getExpireAt()).isEqualTo(LocalDate.now().plusYears(1));
    }

    @Test
    @DisplayName("earnPointByDelivery 성공 - 적립 기준 금액이 0 이하이면 적립하지 않는다")
    void earnPointByDelivery_success_skip_whenBaseAmountIsZeroOrLess() {
        // given
        Long orderId = 1L;
        Long userId = 1L;

        User user = mockUser(userId);
        Point point = createPointWithBalance(
            user,
            0
        );
        setField(point, "id", 100L);

        Order order = mock(Order.class);

        when(order.getUser()).thenReturn(user);
        when(order.getTotalPrice()).thenReturn(1_000L);
        when(order.getDiscountPrice()).thenReturn(1_000L);
        when(order.getUsedPoint()).thenReturn(0);

        when(orderItemRepository.isAllDelivered(orderId))
            .thenReturn(true);

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        when(pointHistoryRepository.findAllByOrderIdAndType(
            orderId,
            PointHistoryType.EARN
        )).thenReturn(List.of());

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        // when
        pointTxService.earnPointByDelivery(orderId);

        // then
        assertThat(point.getBalance()).isZero();

        verify(pointRepository, never()).save(any(Point.class));
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("earnPointByDelivery 성공 - 적립 포인트가 0 이하이면 적립하지 않는다")
    void earnPointByDelivery_success_skip_whenEarnAmountIsZeroOrLess() {
        // given
        Long orderId = 1L;
        Long userId = 1L;

        User user = mockUser(userId);
        Point point = createPointWithBalance(
            user,
            0
        );
        setField(point, "id", 100L);

        Order order = mock(Order.class);

        when(order.getUser()).thenReturn(user);
        when(order.getTotalPrice()).thenReturn(99L);
        when(order.getDiscountPrice()).thenReturn(0L);
        when(order.getUsedPoint()).thenReturn(0);

        when(orderItemRepository.isAllDelivered(orderId))
            .thenReturn(true);

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        when(pointHistoryRepository.findAllByOrderIdAndType(
            orderId,
            PointHistoryType.EARN
        )).thenReturn(List.of());

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        // when
        pointTxService.earnPointByDelivery(orderId);

        // then
        assertThat(point.getBalance()).isZero();

        verify(pointRepository, never()).save(any(Point.class));
        verify(pointHistoryRepository, never()).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("earnPointByDelivery 성공 - 정상 적립 시 EARN 이력을 저장하고 balance를 증가시킨다")
    void earnPointByDelivery_success_saveEarnHistoryAndIncreaseBalance() {
        // given
        Long orderId = 1L;
        Long userId = 1L;

        User user = mockUser(userId);
        Point point = createPointWithBalance(
            user,
            1000
        );
        setField(point, "id", 100L);

        Order order = mock(Order.class);

        when(order.getUser()).thenReturn(user);
        when(order.getTotalPrice()).thenReturn(10_000L);
        when(order.getDiscountPrice()).thenReturn(0L);
        when(order.getUsedPoint()).thenReturn(0);

        when(orderItemRepository.isAllDelivered(orderId))
            .thenReturn(true);

        when(orderRepository.findById(orderId))
            .thenReturn(Optional.of(order));

        when(pointHistoryRepository.findAllByOrderIdAndType(
            orderId,
            PointHistoryType.EARN
        )).thenReturn(List.of());

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        // when
        pointTxService.earnPointByDelivery(orderId);

        // then
        assertThat(point.getBalance()).isEqualTo(1100);

        verify(pointRepository, never()).save(any(Point.class));

        ArgumentCaptor<PointHistory> historyCaptor =
            ArgumentCaptor.forClass(PointHistory.class);

        verify(pointHistoryRepository).save(historyCaptor.capture());

        PointHistory savedHistory = historyCaptor.getValue();

        assertThat(savedHistory.getPoint()).isSameAs(point);
        assertThat(savedHistory.getUser()).isSameAs(user);
        assertThat(savedHistory.getOrder()).isSameAs(order);
        assertThat(savedHistory.getAmount()).isEqualTo(100);
        assertThat(savedHistory.getRemainingAmount()).isEqualTo(100);
        assertThat(savedHistory.getType()).isEqualTo(PointHistoryType.EARN);
        assertThat(savedHistory.getDescription()).isEqualTo("주문 #1 배송 완료 적립 (1%)");
        assertThat(savedHistory.getExpireAt()).isEqualTo(LocalDate.now().plusYears(1));
    }

}
