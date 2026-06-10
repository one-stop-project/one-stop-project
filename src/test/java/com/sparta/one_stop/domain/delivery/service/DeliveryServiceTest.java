package com.sparta.one_stop.domain.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.delivery.dto.request.RejectOrderRequest;
import com.sparta.one_stop.domain.delivery.dto.request.ShipDeliveryRequest;
import com.sparta.one_stop.domain.delivery.dto.request.UpdateDeliveryStatusRequest;
import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.delivery.entity.DeliveryHistory;
import com.sparta.one_stop.domain.delivery.repository.DeliveryHistoryRepository;
import com.sparta.one_stop.domain.delivery.repository.DeliveryRepository;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.outbox.service.OutboxEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DeliveryHistoryRepository deliveryHistoryRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private OutboxEventService outboxEventService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DeliveryService deliveryService;

    @Test
    @DisplayName("발주 확인 성공")
    void confirmOrder_success() {

        Long userId = 1L;
        Long orderItemId = 1L;

        Seller seller = mock(Seller.class);
        OrderItem orderItem = mock(OrderItem.class);
        Delivery delivery = mock(Delivery.class);

        when(sellerRepository.findByUserId(userId))
            .thenReturn(Optional.of(seller));

        when(seller.getId()).thenReturn(1L);

        when(orderItemRepository.findById(orderItemId))
            .thenReturn(Optional.of(orderItem));

        when(orderItem.getSeller()).thenReturn(seller);

        when(orderItem.getStatus())
            .thenReturn(OrderItemStatus.ORDERED);

        when(deliveryRepository.findByOrderItem(orderItem))
            .thenReturn(Optional.of(delivery));

        deliveryService.confirmOrder(
            orderItemId,
            userId
        );

        verify(orderItem).confirm();
        verify(delivery).confirm();

        verify(deliveryHistoryRepository)
            .save(any(DeliveryHistory.class));
    }

    @Test
    @DisplayName("발주 확인 실패 - 다른 판매자")
    void confirmOrder_fail_owner() {

        Long userId = 1L;

        Seller loginSeller = mock(Seller.class);
        Seller orderSeller = mock(Seller.class);

        OrderItem orderItem = mock(OrderItem.class);

        when(sellerRepository.findByUserId(userId))
            .thenReturn(Optional.of(loginSeller));

        when(loginSeller.getId()).thenReturn(1L);
        when(orderSeller.getId()).thenReturn(2L);

        when(orderItemRepository.findById(1L))
            .thenReturn(Optional.of(orderItem));

        when(orderItem.getSeller())
            .thenReturn(orderSeller);

        assertThatThrownBy(() ->
            deliveryService.confirmOrder(
                1L,
                userId
            )
        ).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("주문 거절 성공")
    void rejectOrder_success() {

        Long userId = 1L;

        Seller seller = mock(Seller.class);

        OrderItem orderItem = mock(OrderItem.class);

        ProductItem productItem = mock(ProductItem.class);

        when(sellerRepository.findByUserId(userId))
            .thenReturn(Optional.of(seller));

        when(seller.getId()).thenReturn(1L);

        when(orderItemRepository.findById(1L))
            .thenReturn(Optional.of(orderItem));

        when(orderItem.getSeller())
            .thenReturn(seller);

        when(orderItem.getStatus())
            .thenReturn(OrderItemStatus.ORDERED);

        when(orderItem.getProductItem())
            .thenReturn(productItem);

        when(orderItem.getPrice())
            .thenReturn(10000L);

        when(orderItem.getQuantity())
            .thenReturn(2);

        deliveryService.rejectOrder(
            1L,
            userId,
            new RejectOrderRequest("재고 없음")
        );

        verify(orderItem).reject();

        verify(productItem)
            .increaseStock(2);
    }

    @Test
    @DisplayName("운송장 등록 성공")
    void shipDelivery_success() {

        Long userId = 1L;

        Seller seller = mock(Seller.class);

        OrderItem orderItem = mock(OrderItem.class);

        Delivery delivery = mock(Delivery.class);

        when(sellerRepository.findByUserId(userId))
            .thenReturn(Optional.of(seller));

        when(seller.getId()).thenReturn(1L);

        when(deliveryRepository.findById(1L))
            .thenReturn(Optional.of(delivery));

        when(delivery.getOrderItem())
            .thenReturn(orderItem);

        when(orderItem.getSeller())
            .thenReturn(seller);

        when(delivery.getStatus())
            .thenReturn(DeliveryStatus.INSTRUCT);

        deliveryService.shipDelivery(
            1L,
            userId,
            new ShipDeliveryRequest(
                "CJ대한통운",
                "123456"
            )
        );

        verify(delivery)
            .registerInvoice(
                "CJ대한통운",
                "123456"
            );

        verify(orderItem)
            .startShipping();

        verify(deliveryHistoryRepository)
            .save(any(DeliveryHistory.class));
    }

    @Test
    @DisplayName("배송 상태 변경 성공")
    void updateDeliveryStatus_success() {

        Long userId = 1L;

        Seller seller = mock(Seller.class);

        OrderItem orderItem = mock(OrderItem.class);

        Delivery delivery = mock(Delivery.class);

        when(sellerRepository.findByUserId(userId))
            .thenReturn(Optional.of(seller));

        when(seller.getId()).thenReturn(1L);

        when(deliveryRepository.findById(1L))
            .thenReturn(Optional.of(delivery));

        when(delivery.getOrderItem())
            .thenReturn(orderItem);

        when(orderItem.getSeller())
            .thenReturn(seller);

        deliveryService.updateDeliveryStatus(
            1L,
            userId,
            new UpdateDeliveryStatusRequest(
                DeliveryStatus.DELIVERING
            )
        );

        verify(delivery)
            .updateStatus(
                DeliveryStatus.DELIVERING
            );

        verify(deliveryHistoryRepository)
            .save(any(DeliveryHistory.class));
    }
}
