package com.sparta.one_stop.domain.order.service;

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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

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
            .orElseThrow(() -> new CustomException(ErrorCode.ORDER_007));

        validateOrderOwner(
            userId,
            order
        );

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);

        List<OrderDetailItemResponse> itemResponses = orderItems.stream()
            .map(this::toOrderDetailItemResponse)
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
     * 주문 상세 상품 응답 DTO 변환
     */
    private OrderDetailItemResponse toOrderDetailItemResponse(OrderItem orderItem) {
        return new OrderDetailItemResponse(
            orderItem.getId(),
            orderItem.getProductItem().getId(),
            orderItem.getItemName(),
            orderItem.getSeller().getId(),
            orderItem.getQuantity(),
            orderItem.getPrice(),
            orderItem.getStatus(),
            null // TODO: 배송 도메인 연동 후 DeliverySummaryResponse 매핑
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
