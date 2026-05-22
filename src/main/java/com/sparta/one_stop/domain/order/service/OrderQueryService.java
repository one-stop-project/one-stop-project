package com.sparta.one_stop.domain.order.service;

import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.delivery.repository.DeliveryRepository;
import com.sparta.one_stop.domain.order.dto.response.OrderDetailItemResponse;
import com.sparta.one_stop.domain.order.dto.response.OrderDetailResponse;
import com.sparta.one_stop.domain.order.dto.response.OrderPageResponse;
import com.sparta.one_stop.domain.order.dto.response.OrderSummaryResponse;
import com.sparta.one_stop.domain.order.dto.response.ReceiverResponse;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DeliveryRepository deliveryRepository;

    /**
     * 내 주문 목록 조회
     */
    public OrderPageResponse getMyOrders(
        Long userId,
        OrderStatus status,
        LocalDate from,
        LocalDate to,
        int page,
        int size
    ) {
        LocalDateTime fromDateTime = from != null
            ? from.atStartOfDay()
            : null;

        LocalDateTime toDateTime = to != null
            ? to.atTime(LocalTime.MAX)
            : null;

        PageRequest pageRequest = PageRequest.of(
            page,
            size
        );

        Page<Order> orders = orderRepository.searchMyOrders(
            userId,
            status,
            fromDateTime,
            toDateTime,
            pageRequest
        );

        List<OrderSummaryResponse> content = orders.getContent()
            .stream()
            .map(this::toOrderSummaryResponse)
            .toList();

        return new OrderPageResponse(
            content,
            orders.getNumber(),
            orders.getSize(),
            orders.getTotalElements(),
            orders.getTotalPages()
        );
    }

    /**
     * 주문 단건 조회
     */
    public OrderDetailResponse getOrder(
        Long userId,
        Long orderId
    ) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CustomException(ErrorCode.ORDER_006));

        validateOrderOwner(
            userId,
            order
        );

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);

        // 주문 상품별 배송 정보를 한 번에 조회한 뒤 orderItemId 기준으로 매핑
        // 상세 응답 생성 시 주문 상품마다 배송 정보를 개별 조회하는 것을 방지
        List<Long> orderItemIds = orderItems.stream()
            .map(OrderItem::getId)
            .toList();

        List<Delivery> deliveries = deliveryRepository.findAllByOrderItemIdIn(orderItemIds);

        Map<Long, Delivery> deliveryMap = deliveries.stream()
            .collect(Collectors.toMap(
                delivery -> delivery.getOrderItem().getId(),
                delivery -> delivery
            ));

        List<OrderDetailItemResponse> itemResponses = orderItems.stream()
            .map(orderItem -> OrderDetailItemResponse.of(
                orderItem,
                deliveryMap.get(orderItem.getId())
            ))
            .toList();

        ReceiverResponse receiver = new ReceiverResponse(
            order.getReceiverName(),
            order.getReceiverPhone(),
            order.getReceiverAddress()
        );

        return new OrderDetailResponse(
            order.getId(),
            order.getStatus(),
            order.getDeliveryFee(),
            itemResponses,
            receiver,
            order.getTotalPrice(),
            order.getDiscountPrice(),
            order.getUsedPoint(),
            order.getFinalPrice(),
            order.getCreatedAt()
        );
    }

    /**
     * 주문 목록 응답 DTO 변환
     * TODO: 주문 목록 조회 시 주문별 orderItems 개별 조회로 N+1 발생 가능
     * 추후 orderId 목록 기반 일괄 조회 또는 Projection 쿼리로 개선 예정
     */
    private OrderSummaryResponse toOrderSummaryResponse(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());

        OrderItem firstItem = orderItems.isEmpty()
            ? null
            : orderItems.get(0);

        String firstItemName = firstItem != null
            ? firstItem.getItemName()
            : null;

        String firstItemThumbnail = firstItem != null
            ? firstItem.getProductItem()
              .getProduct()
              .getThumbnailUrl()
            : null;

        int itemCount = orderItems.stream()
            .mapToInt(OrderItem::getQuantity)
            .sum();

        return new OrderSummaryResponse(
            order.getId(),
            order.getFinalPrice(),
            order.getStatus(),
            itemCount,
            firstItemName,
            firstItemThumbnail,
            order.getCreatedAt()
        );
    }

    /**
     * 주문 소유자 검증
     */
    private void validateOrderOwner(
        Long userId,
        Order order
    ) {
        if (!order.getUser()
            .getId()
            .equals(userId)) {
            throw new CustomException(ErrorCode.ORDER_007);
        }
    }
}
