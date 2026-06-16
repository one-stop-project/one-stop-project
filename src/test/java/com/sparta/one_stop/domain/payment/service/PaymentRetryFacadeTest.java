package com.sparta.one_stop.domain.payment.service;

import com.sparta.one_stop.domain.payment.dto.request.ApprovePaymentRequest;
import com.sparta.one_stop.domain.payment.dto.response.ApprovePaymentResponse;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRetryFacadeTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentRetryFacade paymentRetryFacade;

    @Test
    @DisplayName("approvePayment 성공 - PaymentService로 결제 승인을 위임한다")
    void approvePayment_success_delegateToPaymentService() {
        // given
        Long userId = 1L;
        Long orderId = 10L;
        Long amount = 30000L;

        ApprovePaymentRequest request = new ApprovePaymentRequest(
            orderId,
            amount
        );

        ApprovePaymentResponse response = new ApprovePaymentResponse(
            orderId,
            amount,
            OrderStatus.PAID,
            LocalDateTime.now()
        );

        when(paymentService.approvePayment(userId, request))
            .thenReturn(response);

        // when
        ApprovePaymentResponse result = paymentRetryFacade.approvePayment(
            userId,
            request
        );

        // then
        assertThat(result).isSameAs(response);

        verify(paymentService).approvePayment(
            userId,
            request
        );
    }

    @Test
    @DisplayName("recoverApprovePayment 실패 - 낙관적 락 재시도 소진 시 PAYMENT_010 예외 발생")
    void recoverApprovePayment_fail_whenOptimisticLockRetryExhausted() {
        // given
        Long userId = 1L;
        Long orderId = 10L;
        Long amount = 30000L;

        ApprovePaymentRequest request = new ApprovePaymentRequest(
            orderId,
            amount
        );

        ObjectOptimisticLockingFailureException optimisticLockException =
            new ObjectOptimisticLockingFailureException(
                "Payment",
                orderId
            );

        // when
        CustomException exception = assertThrows(
            CustomException.class,
            () -> paymentRetryFacade.recoverApprovePayment(
                optimisticLockException,
                userId,
                request
            )
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_010);
    }

}
