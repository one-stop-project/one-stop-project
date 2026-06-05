package com.sparta.one_stop.domain.point.service;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.point.dto.request.PointChargeRequest;
import com.sparta.one_stop.domain.point.dto.response.ExpiringSoonPointResponse;
import com.sparta.one_stop.domain.point.dto.response.PointChargeResponse;
import com.sparta.one_stop.domain.point.dto.response.PointHistoryPageResponse;
import com.sparta.one_stop.domain.point.dto.response.PointResponse;
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @Mock
    private PointTxService pointTxService;

    @InjectMocks
    private PointService pointService;

    private static PointResponse pointResponse() {
        return new PointResponse(
            0,
            new ExpiringSoonPointResponse(
                0,
                null
            ),
            new PointHistoryPageResponse(
                List.of(),
                0,
                20,
                0,
                0
            )
        );
    }

    private static Order mockOrder() {
        return mock(Order.class);
    }

    private static Order mockOrder(Long orderId) {
        Order order = mock(Order.class);

        when(order.getId()).thenReturn(orderId);

        return order;
    }

    private static ObjectOptimisticLockingFailureException optimisticLockException() {
        return new ObjectOptimisticLockingFailureException(
            PointService.class,
            1L
        );
    }

    @Test
    @DisplayName("getMyPoints 성공 - PointTxService에 위임한다")
    void getMyPoints_success_delegatesToPointTxService() {
        // given
        Long userId = 1L;
        PointHistoryType type = PointHistoryType.CHARGE;
        Pageable pageable = PageRequest.of(
            0,
            20
        );

        PointResponse expectedResponse = pointResponse();

        when(pointTxService.getMyPoints(
            userId,
            type,
            pageable
        )).thenReturn(expectedResponse);

        // when
        PointResponse response = pointService.getMyPoints(
            userId,
            type,
            pageable
        );

        // then
        assertThat(response).isSameAs(expectedResponse);

        verify(pointTxService).getMyPoints(
            userId,
            type,
            pageable
        );
    }

    @Test
    @DisplayName("chargePoint 성공 - PointTxService에 위임한다")
    void chargePoint_success_delegatesToPointTxService() {
        // given
        Long userId = 1L;
        PointChargeRequest request = new PointChargeRequest(5000);
        PointChargeResponse expectedResponse = new PointChargeResponse(
            5000,
            5000,
            LocalDate.now().plusYears(1)
        );

        when(pointTxService.chargePoint(
            userId,
            request
        )).thenReturn(expectedResponse);

        // when
        PointChargeResponse response = pointService.chargePoint(
            userId,
            request
        );

        // then
        assertThat(response).isSameAs(expectedResponse);

        verify(pointTxService).chargePoint(
            userId,
            request
        );
    }

    @Test
    @DisplayName("usePoint 성공 - PointTxService에 위임한다")
    void usePoint_success_delegatesToPointTxService() {
        // given
        Long userId = 1L;
        Order order = mockOrder();
        Integer usedPoint = 3000;

        // when
        pointService.usePoint(
            userId,
            order,
            usedPoint
        );

        // then
        verify(pointTxService).usePoint(
            userId,
            order,
            usedPoint
        );
    }

    @Test
    @DisplayName("refundPointByOrder 성공 - PointTxService에 위임한다")
    void refundPointByOrder_success_delegatesToPointTxService() {
        // given
        Order order = mockOrder();
        Integer expectedRefundPoint = 3000;

        when(pointTxService.refundPointByOrder(order))
            .thenReturn(expectedRefundPoint);

        // when
        Integer refundPoint = pointService.refundPointByOrder(order);

        // then
        assertThat(refundPoint).isEqualTo(expectedRefundPoint);

        verify(pointTxService).refundPointByOrder(order);
    }

    @Test
    @DisplayName("recoverChargePoint 실패 - 재시도 3회 실패 시 POINT_005 예외가 발생한다")
    void recoverChargePoint_fail_whenRetryExhausted() {
        // given
        ObjectOptimisticLockingFailureException exception =
            optimisticLockException();

        Long userId = 1L;
        PointChargeRequest request = new PointChargeRequest(5000);

        // when & then
        assertThatThrownBy(() -> pointService.recoverChargePoint(
            exception,
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> {
                CustomException customException = (CustomException) e;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_005);
            });
    }

    @Test
    @DisplayName("recoverChargePoint 실패 - request가 null이어도 POINT_005 예외가 발생한다")
    void recoverChargePoint_fail_whenRequestIsNull() {
        // given
        ObjectOptimisticLockingFailureException exception =
            optimisticLockException();

        // when & then
        assertThatThrownBy(() -> pointService.recoverChargePoint(
            exception,
            1L,
            null
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> {
                CustomException customException = (CustomException) e;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_005);
            });
    }

    @Test
    @DisplayName("recoverUsePoint 실패 - 재시도 3회 실패 시 POINT_005 예외가 발생한다")
    void recoverUsePoint_fail_whenRetryExhausted() {
        // given
        ObjectOptimisticLockingFailureException exception =
            optimisticLockException();

        Long userId = 1L;
        Order order = mockOrder(10L);
        Integer usedPoint = 3000;

        // when & then
        assertThatThrownBy(() -> pointService.recoverUsePoint(
            exception,
            userId,
            order,
            usedPoint
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> {
                CustomException customException = (CustomException) e;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_005);
            });
    }

    @Test
    @DisplayName("recoverUsePoint 실패 - order가 null이어도 POINT_005 예외가 발생한다")
    void recoverUsePoint_fail_whenOrderIsNull() {
        // given
        ObjectOptimisticLockingFailureException exception =
            optimisticLockException();

        // when & then
        assertThatThrownBy(() -> pointService.recoverUsePoint(
            exception,
            1L,
            null,
            3000
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> {
                CustomException customException = (CustomException) e;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_005);
            });
    }

    @Test
    @DisplayName("recoverRefundPointByOrder 실패 - 재시도 3회 실패 시 POINT_005 예외가 발생한다")
    void recoverRefundPointByOrder_fail_whenRetryExhausted() {
        // given
        ObjectOptimisticLockingFailureException exception =
            optimisticLockException();

        Order order = mockOrder(10L);

        // when & then
        assertThatThrownBy(() -> pointService.recoverRefundPointByOrder(
            exception,
            order
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> {
                CustomException customException = (CustomException) e;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_005);
            });
    }

    @Test
    @DisplayName("recoverRefundPointByOrder 실패 - order가 null이어도 POINT_005 예외가 발생한다")
    void recoverRefundPointByOrder_fail_whenOrderIsNull() {
        // given
        ObjectOptimisticLockingFailureException exception =
            optimisticLockException();

        // when & then
        assertThatThrownBy(() -> pointService.recoverRefundPointByOrder(
            exception,
            null
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(e -> {
                CustomException customException = (CustomException) e;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_005);
            });
    }

}
