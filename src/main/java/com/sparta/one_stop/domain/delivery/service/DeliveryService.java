package com.sparta.one_stop.domain.delivery.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.one_stop.domain.delivery.dto.request.RejectOrderRequest;
import com.sparta.one_stop.domain.delivery.dto.request.ShipDeliveryRequest;
import com.sparta.one_stop.domain.delivery.dto.request.UpdateDeliveryStatusRequest;
import com.sparta.one_stop.domain.delivery.dto.response.ConfirmOrderResponse;
import com.sparta.one_stop.domain.delivery.dto.response.DeliveryHistoryResponse;
import com.sparta.one_stop.domain.delivery.dto.response.DeliveryResponse;
import com.sparta.one_stop.domain.delivery.dto.response.RejectOrderResponse;
import com.sparta.one_stop.domain.delivery.dto.response.SellerOrderResponse;
import com.sparta.one_stop.domain.delivery.dto.response.ShipDeliveryResponse;
import com.sparta.one_stop.domain.delivery.dto.response.UpdateDeliveryStatusResponse;
import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.delivery.entity.DeliveryHistory;
import com.sparta.one_stop.domain.delivery.event.DeliveryCompletedEventPayload;
import com.sparta.one_stop.domain.delivery.repository.DeliveryHistoryRepository;
import com.sparta.one_stop.domain.delivery.repository.DeliveryRepository;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.outbox.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryHistoryRepository deliveryHistoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SellerRepository sellerRepository;
    private final OutboxEventService outboxEventService;
    private final ObjectMapper objectMapper;

    // userId → Seller 조회 헬퍼
    private Seller findSellerByUserId(Long userId) {
        return sellerRepository.findByUserId(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.SELLER_001));
    }

    // 구매자 배송 목록 조회
    public List<DeliveryResponse> getDeliveries(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CustomException(ErrorCode.ORDER_006));

        if (!order.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ORDER_007);
        }

        List<Delivery> deliveries =
            deliveryRepository.findAllByOrderItem_Order_Id(orderId);

        return deliveries.stream()
            .map(DeliveryResponse::from)
            .toList();
    }

    // 배송 이력 조회
    public DeliveryHistoryResponse getDeliveryHistory(Long deliveryId, Long userId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new CustomException(ErrorCode.SHIPPING_005));

        if (!delivery.getOrderItem().getOrder().getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.ORDER_007);
        }

        List<DeliveryHistory> histories =
            deliveryHistoryRepository.findAllByDeliveryIdOrderByChangedAtAsc(deliveryId);

        return DeliveryHistoryResponse.from(delivery, histories);
    }

    // 판매자 주문 목록 조회
    public Page<SellerOrderResponse> getSellerOrders(Long userId,
                                                     OrderItemStatus status,
                                                     Pageable pageable) {
        Seller seller = findSellerByUserId(userId);

        Page<OrderItem> orderItems;

        if (status != null) {
            orderItems = orderItemRepository.findBySeller_IdAndStatus(
                seller.getId(),
                status,
                pageable
            );
        } else {
            orderItems = orderItemRepository.findBySeller_Id(
                seller.getId(),
                pageable
            );
        }

        return orderItems.map(orderItem -> {
            Delivery delivery = deliveryRepository.findByOrderItem(orderItem)
                .orElseThrow(() -> new CustomException(ErrorCode.SHIPPING_005));

            return SellerOrderResponse.from(orderItem, delivery);
        });
    }

    // 결제 완료 후 배송 생성
    // - OrderItem 상태를 ORDERED로 변경
    // - 주문 상품별 Delivery 생성
    // - 최초 배송 상태 ACCEPT 이력 생성
    @Transactional
    public void createDeliveriesForPayment(Order order) {

        List<OrderItem> orderItems =
            orderItemRepository.findAllByOrderId(order.getId());

        for (OrderItem orderItem : orderItems) {

            orderItem.markOrdered();

            Delivery delivery = Delivery.builder()
                .orderItem(orderItem)
                .build();

            deliveryRepository.save(delivery);

            deliveryHistoryRepository.save(
                DeliveryHistory.builder()
                    .delivery(delivery)
                    .status(DeliveryStatus.ACCEPT)
                    .build()
            );
        }
    }

    // 발주 확인 (ORDERED → CONFIRMED / ACCEPT → INSTRUCT)
    @Transactional
    public ConfirmOrderResponse confirmOrder(Long orderItemId, Long userId) {
        Seller seller = findSellerByUserId(userId);

        OrderItem orderItem = orderItemRepository.findById(orderItemId)
            .orElseThrow(() -> new CustomException(ErrorCode.ORDER_006));

        if (!orderItem.getSeller().getId().equals(seller.getId())) {
            throw new CustomException(ErrorCode.SELLER_007);
        }

        if (orderItem.getStatus() != OrderItemStatus.ORDERED) {
            throw new CustomException(ErrorCode.SELLER_008);
        }

        orderItem.confirm();

        Delivery delivery = deliveryRepository.findByOrderItem(orderItem)
            .orElseThrow(() -> new CustomException(ErrorCode.SHIPPING_005));

        delivery.confirm();

        deliveryHistoryRepository.save(
            new DeliveryHistory(delivery, DeliveryStatus.INSTRUCT)
        );

        return ConfirmOrderResponse.of(
            orderItemId,
            orderItem.getStatus(),
            delivery.getStatus()
        );
    }

    // 주문 거절 (ORDERED → REJECTED)
    @Transactional
    public RejectOrderResponse rejectOrder(Long orderItemId,
                                           Long userId,
                                           RejectOrderRequest request) {

        Seller seller = findSellerByUserId(userId);

        OrderItem orderItem = orderItemRepository.findById(orderItemId)
            .orElseThrow(() -> new CustomException(ErrorCode.ORDER_006));

        if (!orderItem.getSeller().getId().equals(seller.getId())) {
            throw new CustomException(ErrorCode.SELLER_007);
        }

        if (orderItem.getStatus() != OrderItemStatus.ORDERED) {
            throw new CustomException(ErrorCode.SELLER_008);
        }

        orderItem.reject();
        orderItem.getProductItem().increaseStock(orderItem.getQuantity());

        Long refundAmount = orderItem.getPrice() * orderItem.getQuantity();

        return RejectOrderResponse.of(
            orderItemId,
            refundAmount,
            orderItem.getQuantity()
        );
    }

    // 운송장 등록 (INSTRUCT → DEPARTURE)
    @Transactional
    public ShipDeliveryResponse shipDelivery(Long deliveryId,
                                             Long userId,
                                             ShipDeliveryRequest request) {

        Seller seller = findSellerByUserId(userId);

        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new CustomException(ErrorCode.SHIPPING_005));

        if (!delivery.getOrderItem().getSeller().getId().equals(seller.getId())) {
            throw new CustomException(ErrorCode.SHIPPING_006);
        }

        if (delivery.getStatus() != DeliveryStatus.INSTRUCT) {
            throw new CustomException(ErrorCode.SHIPPING_001);
        }

        delivery.registerInvoice(
            request.deliveryCompany(),
            request.invoiceNumber()
        );

        delivery.getOrderItem().startShipping();

        deliveryHistoryRepository.save(
            new DeliveryHistory(delivery, DeliveryStatus.DEPARTURE)
        );

        return ShipDeliveryResponse.from(delivery);
    }

    // 배송 상태 변경 (DEPARTURE → DELIVERING → FINAL_DELIVERY)
    @Transactional
    public UpdateDeliveryStatusResponse updateDeliveryStatus(Long deliveryId,
                                                             Long userId,
                                                             UpdateDeliveryStatusRequest request) {

        Seller seller = findSellerByUserId(userId);

        Delivery delivery = deliveryRepository.findById(deliveryId)
            .orElseThrow(() -> new CustomException(ErrorCode.SHIPPING_005));

        if (!delivery.getOrderItem().getSeller().getId().equals(seller.getId())) {
            throw new CustomException(ErrorCode.SHIPPING_006);
        }

        delivery.updateStatus(request.status());

        deliveryHistoryRepository.save(
            new DeliveryHistory(delivery, request.status())
        );

        // FINAL_DELIVERY 전환 시 OrderItem 상태 변경 + Outbox 이벤트 저장
        if (request.status() == DeliveryStatus.FINAL_DELIVERY) {
            delivery.getOrderItem().completeDelivery();
            saveDeliveryCompletedOutboxEvent(delivery);
        }

        return UpdateDeliveryStatusResponse.from(delivery);
    }

    /**
     * 배송 완료 Outbox 이벤트 저장
     * - 배송 완료 트랜잭션과 동일 트랜잭션 내에서 처리
     * - 트랜잭션 롤백 시 Outbox 이벤트도 함께 롤백
     * - Outbox Publisher가 Kafka로 발행 → Consumer가 포인트 적립 처리
     */
    private void saveDeliveryCompletedOutboxEvent(Delivery delivery) {
        OrderItem orderItem = delivery.getOrderItem();
        Order order = orderItem.getOrder();

        String eventId = "delivery-completed-" + delivery.getId();

        DeliveryCompletedEventPayload payload = DeliveryCompletedEventPayload.of(
            eventId,
            order.getId(),
            order.getUser().getId(),
            delivery.getId(),
            LocalDateTime.now()
        );

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            outboxEventService.saveDeliveryCompletedEvent(
                eventId,
                order.getId(),
                payloadJson
            );
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.COMMON_007);
        }
    }
}
