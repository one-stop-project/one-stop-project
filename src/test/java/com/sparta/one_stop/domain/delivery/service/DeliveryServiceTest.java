package com.sparta.one_stop.domain.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.delivery.dto.request.RejectOrderRequest;
import com.sparta.one_stop.domain.delivery.dto.request.ShipDeliveryRequest;
import com.sparta.one_stop.domain.delivery.dto.request.UpdateDeliveryStatusRequest;
import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.delivery.entity.DeliveryHistory;
import com.sparta.one_stop.domain.delivery.repository.DeliveryHistoryRepository;
import com.sparta.one_stop.domain.delivery.repository.DeliveryRepository;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderCancelHistory;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderCancelHistoryRepository;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.order.service.OrderCommandService;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.outbox.service.OutboxEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock private DeliveryRepository deliveryRepository;
    @Mock private DeliveryHistoryRepository deliveryHistoryRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private SellerRepository sellerRepository;
    @Mock private OutboxEventService outboxEventService;
    @Mock private ObjectMapper objectMapper;
    @Mock private OrderCommandService orderCommandService;
    @Mock private OrderCancelHistoryRepository orderCancelHistoryRepository;

    @InjectMocks
    private DeliveryService deliveryService;

    private Seller mockSeller(Long sellerId, Long userId) {
        Seller seller = mock(Seller.class);
        lenient().when(seller.getId()).thenReturn(sellerId);
        when(sellerRepository.findByUserId(userId)).thenReturn(Optional.of(seller));
        return seller;
    }

    private OrderItem mockOrderItem(Long orderItemId, Seller seller, OrderItemStatus status) {
        OrderItem oi = mock(OrderItem.class);
        lenient().when(oi.getId()).thenReturn(orderItemId);
        lenient().when(oi.getSeller()).thenReturn(seller);
        lenient().when(oi.getStatus()).thenReturn(status);
        when(orderItemRepository.findById(orderItemId)).thenReturn(Optional.of(oi));
        return oi;
    }

    private Delivery mockDelivery(Long deliveryId, OrderItem orderItem, DeliveryStatus status) {
        Delivery delivery = mock(Delivery.class);
        lenient().when(delivery.getId()).thenReturn(deliveryId);
        lenient().when(delivery.getOrderItem()).thenReturn(orderItem);
        lenient().when(delivery.getStatus()).thenReturn(status);
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
        return delivery;
    }

    @Nested
    @DisplayName("발주 확인")
    class ConfirmOrder {

        @Test
        @DisplayName("성공 — ORDERED 상태에서 CONFIRMED 전이")
        void success() {
            Long userId = 1L;
            Seller seller = mockSeller(1L, userId);
            OrderItem oi = mockOrderItem(1L, seller, OrderItemStatus.ORDERED);

            Delivery delivery = mock(Delivery.class);
            when(deliveryRepository.findByOrderItem(oi)).thenReturn(Optional.of(delivery));

            deliveryService.confirmOrder(1L, userId);

            verify(oi).confirm();
            verify(delivery).confirm();
            verify(deliveryHistoryRepository).save(any(DeliveryHistory.class));
        }

        @Test
        @DisplayName("실패 — 다른 판매자 (SELLER_007)")
        void fail_differentSeller() {
            Long userId = 1L;
            Seller loginSeller = mockSeller(1L, userId);

            Seller otherSeller = mock(Seller.class);
            when(otherSeller.getId()).thenReturn(2L);

            OrderItem oi = mock(OrderItem.class);
            when(oi.getSeller()).thenReturn(otherSeller);
            when(orderItemRepository.findById(1L)).thenReturn(Optional.of(oi));

            assertThatThrownBy(() -> deliveryService.confirmOrder(1L, userId))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SELLER_007));
        }

        @Test
        @DisplayName("실패 — ORDERED 아닌 상태 (SELLER_008)")
        void fail_notOrdered() {
            Long userId = 1L;
            Seller seller = mockSeller(1L, userId);
            mockOrderItem(1L, seller, OrderItemStatus.CONFIRMED);

            assertThatThrownBy(() -> deliveryService.confirmOrder(1L, userId))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SELLER_008));

            verify(deliveryRepository, never()).findByOrderItem(any());
        }
    }

    @Nested
    @DisplayName("주문 거절")
    class RejectOrder {

        @Test
        @DisplayName("성공 — 재고 복구 + Delivery ORDER_CANCELLED + History 저장")
        void success() {
            Long userId = 1L;
            Seller seller = mockSeller(1L, userId);

            OrderItem oi = mockOrderItem(1L, seller, OrderItemStatus.ORDERED);
            ProductItem pi = mock(ProductItem.class);
            Order order = mock(Order.class);

            when(oi.getProductItem()).thenReturn(pi);
            when(oi.getPrice()).thenReturn(10000L);
            when(oi.getQuantity()).thenReturn(2);
            when(oi.getOrder()).thenReturn(order);
            when(order.getId()).thenReturn(100L);

            // 거절 후 status 변경 시뮬레이션
            when(oi.getStatus())
                .thenReturn(OrderItemStatus.ORDERED)
                .thenReturn(OrderItemStatus.REJECTED);

            Delivery delivery = mock(Delivery.class);
            when(deliveryRepository.findByOrderItemId(1L)).thenReturn(Optional.of(delivery));
            when(delivery.getStatus()).thenReturn(DeliveryStatus.ORDER_CANCELLED);

            // 다른 아이템이 있어서 자동 취소 안 됨
            OrderItem otherItem = mock(OrderItem.class);
            when(otherItem.getStatus()).thenReturn(OrderItemStatus.ORDERED);
            when(orderItemRepository.findAllByOrderId(100L)).thenReturn(List.of(oi, otherItem));
            when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
            when(order.getStatus()).thenReturn(OrderStatus.PAID);

            deliveryService.rejectOrder(1L, userId, new RejectOrderRequest("재고 없음"));

            verify(oi).reject();
            verify(pi).increaseStock(2);
            verify(delivery).cancelOrder();
            verify(deliveryHistoryRepository).save(any(DeliveryHistory.class));
            verify(orderCancelHistoryRepository).save(any(OrderCancelHistory.class));
        }

        @Test
        @DisplayName("실패 — 다른 판매자 (SELLER_007)")
        void fail_differentSeller() {
            Long userId = 1L;
            Seller loginSeller = mockSeller(1L, userId);

            Seller otherSeller = mock(Seller.class);
            when(otherSeller.getId()).thenReturn(2L);

            OrderItem oi = mock(OrderItem.class);
            when(oi.getSeller()).thenReturn(otherSeller);
            when(orderItemRepository.findById(1L)).thenReturn(Optional.of(oi));

            assertThatThrownBy(() ->
                deliveryService.rejectOrder(1L, userId, new RejectOrderRequest("재고 없음"))
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SELLER_007));

            verify(oi, never()).reject();
        }

        @Test
        @DisplayName("실패 — ORDERED 아닌 상태 (SELLER_008)")
        void fail_notOrdered() {
            Long userId = 1L;
            Seller seller = mockSeller(1L, userId);
            mockOrderItem(1L, seller, OrderItemStatus.CONFIRMED);

            assertThatThrownBy(() ->
                deliveryService.rejectOrder(1L, userId, new RejectOrderRequest("사유"))
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SELLER_008));
        }
    }

    // ──────────────────────────────────────────────
    // 운송장 등록 (shipDelivery)
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("운송장 등록")
    class ShipDelivery {

        @Test
        @DisplayName("성공 — INSTRUCT → DEPARTURE + startShipping")
        void success() {
            Long userId = 1L;
            Seller seller = mockSeller(1L, userId);
            OrderItem oi = mock(OrderItem.class);
            when(oi.getSeller()).thenReturn(seller);

            Delivery delivery = mockDelivery(1L, oi, DeliveryStatus.INSTRUCT);

            deliveryService.shipDelivery(1L, userId, new ShipDeliveryRequest("CJ대한통운", "123456"));

            verify(delivery).registerInvoice("CJ대한통운", "123456");
            verify(oi).startShipping();
            verify(deliveryHistoryRepository).save(any(DeliveryHistory.class));
        }

        @Test
        @DisplayName("실패 — INSTRUCT 아닌 상태 (SHIPPING_001)")
        void fail_notInstruct() {
            Long userId = 1L;
            Seller seller = mockSeller(1L, userId);
            OrderItem oi = mock(OrderItem.class);
            when(oi.getSeller()).thenReturn(seller);

            mockDelivery(1L, oi, DeliveryStatus.ACCEPT);

            assertThatThrownBy(() ->
                deliveryService.shipDelivery(1L, userId, new ShipDeliveryRequest("CJ대한통운", "123456"))
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SHIPPING_001));
        }

        @Test
        @DisplayName("실패 — 다른 판매자 (SHIPPING_006)")
        void fail_differentSeller() {
            Long userId = 1L;
            mockSeller(1L, userId);

            Seller otherSeller = mock(Seller.class);
            when(otherSeller.getId()).thenReturn(2L);
            OrderItem oi = mock(OrderItem.class);
            when(oi.getSeller()).thenReturn(otherSeller);

            mockDelivery(1L, oi, DeliveryStatus.INSTRUCT);

            assertThatThrownBy(() ->
                deliveryService.shipDelivery(1L, userId, new ShipDeliveryRequest("CJ대한통운", "123456"))
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SHIPPING_006));
        }
    }

    @Nested
    @DisplayName("배송 상태 변경")
    class UpdateDeliveryStatus {

        @Test
        @DisplayName("성공 — DEPARTURE → DELIVERING")
        void success_delivering() {
            Long userId = 1L;
            Seller seller = mockSeller(1L, userId);
            OrderItem oi = mock(OrderItem.class);
            when(oi.getSeller()).thenReturn(seller);

            mockDelivery(1L, oi, DeliveryStatus.DEPARTURE);

            deliveryService.updateDeliveryStatus(
                1L, userId, new UpdateDeliveryStatusRequest(DeliveryStatus.DELIVERING)
            );

            verify(deliveryHistoryRepository).save(any(DeliveryHistory.class));
        }

        @Test
        @DisplayName("실패 — DELIVERING/FINAL_DELIVERY 외 상태 요청 (SHIPPING_002)")
        void fail_invalidRequestStatus() {
            Long userId = 1L;
            mockSeller(1L, userId);

            assertThatThrownBy(() ->
                deliveryService.updateDeliveryStatus(
                    1L, userId, new UpdateDeliveryStatusRequest(DeliveryStatus.ACCEPT)
                )
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SHIPPING_002));

            // Delivery 조회조차 하지 않음
            verify(deliveryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("실패 — 다른 판매자 (SHIPPING_006)")
        void fail_differentSeller() {
            Long userId = 1L;
            mockSeller(1L, userId);

            Seller otherSeller = mock(Seller.class);
            when(otherSeller.getId()).thenReturn(2L);
            OrderItem oi = mock(OrderItem.class);
            when(oi.getSeller()).thenReturn(otherSeller);

            mockDelivery(1L, oi, DeliveryStatus.DEPARTURE);

            assertThatThrownBy(() ->
                deliveryService.updateDeliveryStatus(
                    1L, userId, new UpdateDeliveryStatusRequest(DeliveryStatus.DELIVERING)
                )
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SHIPPING_006));
        }
    }
}
