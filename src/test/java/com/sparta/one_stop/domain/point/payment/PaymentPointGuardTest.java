package com.sparta.one_stop.domain.point.payment;

import com.sparta.one_stop.domain.point.entity.Point;
import com.sparta.one_stop.domain.point.repository.PointRepository;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentPointGuardTest {

    @Mock
    private PointRepository pointRepository;

    @InjectMocks
    private PaymentPointGuard paymentPointGuard;

    @Test
    @DisplayName("validateOnOrderCreation 실패 - 사용 포인트가 null이면 POINT_002 예외가 발생한다")
    void validateOnOrderCreation_fail_whenRequestedPointIsNull() {
        // given
        Long userId = 1L;

        // when & then
        assertThatThrownBy(() -> paymentPointGuard.validateOnOrderCreation(
            userId,
            null
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_002);
            });

        verify(pointRepository, never()).findByUserId(userId);
    }

    @Test
    @DisplayName("validateOnOrderCreation 실패 - 사용 포인트가 음수이면 POINT_002 예외가 발생한다")
    void validateOnOrderCreation_fail_whenRequestedPointIsNegative() {
        // given
        Long userId = 1L;
        Integer requestedPoint = -1;

        // when & then
        assertThatThrownBy(() -> paymentPointGuard.validateOnOrderCreation(
            userId,
            requestedPoint
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_002);
            });

        verify(pointRepository, never()).findByUserId(userId);
    }

    @Test
    @DisplayName("validateOnOrderCreation 성공 - 사용 포인트가 0이면 포인트 계정을 조회하지 않는다")
    void validateOnOrderCreation_success_whenRequestedPointIsZero() {
        // given
        Long userId = 1L;
        Integer requestedPoint = 0;

        // when & then
        assertThatCode(() -> paymentPointGuard.validateOnOrderCreation(
            userId,
            requestedPoint
        )).doesNotThrowAnyException();

        verify(pointRepository, never()).findByUserId(userId);
    }

    @Test
    @DisplayName("validateOnOrderCreation 실패 - 포인트 계정이 없으면 POINT_001 예외가 발생한다")
    void validateOnOrderCreation_fail_whenPointDoesNotExist() {
        // given
        Long userId = 1L;
        Integer requestedPoint = 1000;

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentPointGuard.validateOnOrderCreation(
            userId,
            requestedPoint
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_001);
            });

        verify(pointRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("validateOnOrderCreation 실패 - 잔액이 부족하면 POINT_002 예외가 발생한다")
    void validateOnOrderCreation_fail_whenBalanceIsNotEnough() {
        // given
        Long userId = 1L;
        Integer requestedPoint = 5000;

        Point point = mock(Point.class);

        when(point.getBalance())
            .thenReturn(1000);

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        // when & then
        assertThatThrownBy(() -> paymentPointGuard.validateOnOrderCreation(
            userId,
            requestedPoint
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_002);
            });

        verify(pointRepository).findByUserId(userId);
        verify(point).verifyIntegrity();
    }

    @Test
    @DisplayName("validateOnOrderCreation 성공 - 잔액이 충분하면 검증을 통과한다")
    void validateOnOrderCreation_success_whenBalanceIsEnough() {
        // given
        Long userId = 1L;
        Integer requestedPoint = 1000;

        Point point = mock(Point.class);

        when(point.getBalance())
            .thenReturn(5000);

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        // when & then
        assertThatCode(() -> paymentPointGuard.validateOnOrderCreation(
            userId,
            requestedPoint
        )).doesNotThrowAnyException();

        verify(pointRepository).findByUserId(userId);
        verify(point).verifyIntegrity();
    }

    @Test
    @DisplayName("validateBeforePaymentApproval 성공 - 사용 포인트가 null이면 포인트 계정을 조회하지 않는다")
    void validateBeforePaymentApproval_success_whenRequestedPointIsNull() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        // when & then
        assertThatCode(() -> paymentPointGuard.validateBeforePaymentApproval(
            userId,
            null,
            orderId
        )).doesNotThrowAnyException();

        verify(pointRepository, never()).findByUserId(userId);
    }

    @Test
    @DisplayName("validateBeforePaymentApproval 성공 - 사용 포인트가 0이면 포인트 계정을 조회하지 않는다")
    void validateBeforePaymentApproval_success_whenRequestedPointIsZero() {
        // given
        Long userId = 1L;
        Integer requestedPoint = 0;
        Long orderId = 10L;

        // when & then
        assertThatCode(() -> paymentPointGuard.validateBeforePaymentApproval(
            userId,
            requestedPoint,
            orderId
        )).doesNotThrowAnyException();

        verify(pointRepository, never()).findByUserId(userId);
    }

    @Test
    @DisplayName("validateBeforePaymentApproval 실패 - 사용 포인트가 음수이면 POINT_003 예외가 발생한다")
    void validateBeforePaymentApproval_fail_whenRequestedPointIsNegative() {
        // given
        Long userId = 1L;
        Integer requestedPoint = -1;
        Long orderId = 10L;

        // when & then
        assertThatThrownBy(() -> paymentPointGuard.validateBeforePaymentApproval(
            userId,
            requestedPoint,
            orderId
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_003);
            });

        verify(pointRepository, never()).findByUserId(userId);
    }

    @Test
    @DisplayName("validateBeforePaymentApproval 실패 - 포인트 계정이 없으면 POINT_001 예외가 발생한다")
    void validateBeforePaymentApproval_fail_whenPointDoesNotExist() {
        // given
        Long userId = 1L;
        Integer requestedPoint = 1000;
        Long orderId = 10L;

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentPointGuard.validateBeforePaymentApproval(
            userId,
            requestedPoint,
            orderId
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_001);
            });

        verify(pointRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("validateBeforePaymentApproval 실패 - 무결성 검증 실패 시 예외를 그대로 전파한다")
    void validateBeforePaymentApproval_fail_whenIntegrityViolationOccurs() {
        // given
        Long userId = 1L;
        Integer requestedPoint = 1000;
        Long orderId = 10L;

        Point point = mock(Point.class);
        CustomException integrityException = new CustomException(ErrorCode.POINT_010);

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        doThrow(integrityException)
            .when(point)
            .verifyIntegrity();

        // when & then
        assertThatThrownBy(() -> paymentPointGuard.validateBeforePaymentApproval(
            userId,
            requestedPoint,
            orderId
        ))
            .isSameAs(integrityException);

        verify(pointRepository).findByUserId(userId);
        verify(point).verifyIntegrity();
    }

    @Test
    @DisplayName("validateBeforePaymentApproval 실패 - 결제 직전 잔액이 부족하면 POINT_002 예외가 발생한다")
    void validateBeforePaymentApproval_fail_whenBalanceIsNotEnough() {
        // given
        Long userId = 1L;
        Integer requestedPoint = 5000;
        Long orderId = 10L;

        Point point = mock(Point.class);

        when(point.getBalance())
            .thenReturn(1000);

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        // when & then
        assertThatThrownBy(() -> paymentPointGuard.validateBeforePaymentApproval(
            userId,
            requestedPoint,
            orderId
        ))
            .isInstanceOf(CustomException.class)
            .satisfies(exception -> {
                CustomException customException = (CustomException) exception;

                assertThat(customException.getErrorCode())
                    .isEqualTo(ErrorCode.POINT_002);
            });

        verify(pointRepository).findByUserId(userId);
        verify(point).verifyIntegrity();
    }

    @Test
    @DisplayName("validateBeforePaymentApproval 성공 - 결제 직전 잔액이 충분하면 검증을 통과한다")
    void validateBeforePaymentApproval_success_whenBalanceIsEnough() {
        // given
        Long userId = 1L;
        Integer requestedPoint = 1000;
        Long orderId = 10L;

        Point point = mock(Point.class);

        when(point.getBalance())
            .thenReturn(5000);

        when(pointRepository.findByUserId(userId))
            .thenReturn(Optional.of(point));

        // when & then
        assertThatCode(() -> paymentPointGuard.validateBeforePaymentApproval(
            userId,
            requestedPoint,
            orderId
        )).doesNotThrowAnyException();

        verify(pointRepository).findByUserId(userId);
        verify(point).verifyIntegrity();
    }

    @Test
    @DisplayName("validateBeforePaymentApproval 트랜잭션 설정 - REQUIRES_NEW readOnly 트랜잭션을 사용한다")
    void validateBeforePaymentApproval_hasRequiresNewReadOnlyTransaction() throws Exception {
        // given
        Method method = PaymentPointGuard.class.getMethod(
            "validateBeforePaymentApproval",
            Long.class,
            Integer.class,
            Long.class
        );

        // when
        Transactional transactional = method.getAnnotation(Transactional.class);

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation())
            .isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(transactional.readOnly()).isTrue();
    }

}
