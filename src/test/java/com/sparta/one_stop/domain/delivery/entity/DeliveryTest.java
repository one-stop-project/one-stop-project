package com.sparta.one_stop.domain.delivery.entity;

import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import com.sparta.one_stop.global.exception.CustomException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryTest {

    @Test
    @DisplayName("발주 확인 성공 - ACCEPT → INSTRUCT")
    void confirm_success() {
        // given
        Delivery delivery = Delivery.builder()
            .orderItem(null)
            .build();

        // when
        delivery.confirm();

        // then
        assertThat(delivery.getStatus())
            .isEqualTo(DeliveryStatus.INSTRUCT);
    }

    @Test
    @DisplayName("운송장 등록 성공 - INSTRUCT → DEPARTURE")
    void registerInvoice_success() {
        // given
        Delivery delivery = Delivery.builder()
            .orderItem(null)
            .build();

        delivery.confirm();

        // when
        delivery.registerInvoice(
            "CJ대한통운",
            "123456789"
        );

        // then
        assertThat(delivery.getStatus())
            .isEqualTo(DeliveryStatus.DEPARTURE);

        assertThat(delivery.getDeliveryCompany())
            .isEqualTo("CJ대한통운");

        assertThat(delivery.getInvoiceNumber())
            .isEqualTo("123456789");
    }

    @Test
    @DisplayName("배송중 변경 성공 - DEPARTURE → DELIVERING")
    void updateStatus_delivering_success() {
        // given
        Delivery delivery = Delivery.builder()
            .orderItem(null)
            .build();

        delivery.confirm();

        delivery.registerInvoice(
            "CJ대한통운",
            "123456789"
        );

        // when
        delivery.updateStatus(
            DeliveryStatus.DELIVERING
        );

        // then
        assertThat(delivery.getStatus())
            .isEqualTo(DeliveryStatus.DELIVERING);
    }

    @Test
    @DisplayName("배송 완료 성공 - DELIVERING → FINAL_DELIVERY")
    void updateStatus_finalDelivery_success() {
        // given
        Delivery delivery = Delivery.builder()
            .orderItem(null)
            .build();

        delivery.confirm();

        delivery.registerInvoice(
            "CJ대한통운",
            "123456789"
        );

        delivery.updateStatus(
            DeliveryStatus.DELIVERING
        );

        // when
        delivery.updateStatus(
            DeliveryStatus.FINAL_DELIVERY
        );

        // then
        assertThat(delivery.getStatus())
            .isEqualTo(DeliveryStatus.FINAL_DELIVERY);
    }

    @Test
    @DisplayName("잘못된 상태 전이 시 예외 발생")
    void invalid_transition_fail() {
        // given
        Delivery delivery = Delivery.builder()
            .orderItem(null)
            .build();

        // when & then
        assertThatThrownBy(() ->
            delivery.updateStatus(
                DeliveryStatus.DELIVERING
            )
        )
            .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("주문 취소 성공")
    void cancelOrder_success() {
        // given
        Delivery delivery = Delivery.builder()
            .orderItem(null)
            .build();

        // when
        delivery.cancelOrder();

        // then
        assertThat(delivery.getStatus())
            .isEqualTo(
                DeliveryStatus.ORDER_CANCELLED
            );
    }

    @Test
    @DisplayName("배송중 상태에서는 주문 취소 불가")
    void cancelOrder_fail() {
        // given
        Delivery delivery = Delivery.builder()
            .orderItem(null)
            .build();

        delivery.confirm();

        delivery.registerInvoice(
            "CJ대한통운",
            "123456789"
        );

        // when & then
        assertThatThrownBy(
            delivery::cancelOrder
        )
            .isInstanceOf(CustomException.class);
    }
}

