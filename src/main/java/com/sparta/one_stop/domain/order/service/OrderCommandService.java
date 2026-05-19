package com.sparta.one_stop.domain.order.service;

import com.sparta.one_stop.domain.cart.entity.CartItem;
import com.sparta.one_stop.domain.cart.repository.CartItemRepository;
import com.sparta.one_stop.domain.order.dto.request.CancelOrderRequest;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderItemRequest;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderRequest;
import com.sparta.one_stop.domain.order.dto.response.CancelOrderResponse;
import com.sparta.one_stop.domain.order.dto.response.CreateOrderItemResponse;
import com.sparta.one_stop.domain.order.dto.response.CreateOrderResponse;
import com.sparta.one_stop.domain.order.dto.response.RestoredCouponResponse;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.OrderStatus;
import com.sparta.one_stop.global.enums.OrderType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderCommandService {

    private static final long MIN_ORDER_PRICE = 1_000L;
    private static final long DEFAULT_DELIVERY_FEE = 3_000L;

    private final UserRepository userRepository;
    private final ProductItemRepository productItemRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * 주문 생성 1단계
     * - DIRECT / CART 주문 분기
     * - 서버 기준 상품 가격 재계산
     * - 재고 검증 및 차감
     * - 주문 상태는 PENDING_PAYMENT 로 생성
     */
    public CreateOrderResponse createOrder(
        Long userId,
        CreateOrderRequest request
    ) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));

        validateOrderType(request);

        List<OrderTarget> orderTargets = getOrderTargets(
            userId,
            request
        );

        List<OrderTarget> sortedTargets = orderTargets.stream()
            .sorted(Comparator.comparing(target -> target.productItem().getId()))
            .toList();

        Long totalPrice = calculateTotalPrice(sortedTargets);

        validateMinimumOrderPrice(totalPrice);

        Long discountPrice = 0L;
        Integer usedPoint = request.usedPoint() != null ? request.usedPoint() : 0;
        Long deliveryFee = DEFAULT_DELIVERY_FEE;
        Long finalPrice = totalPrice - discountPrice - usedPoint + deliveryFee;

        Order order = new Order(
            user,
            totalPrice,
            discountPrice,
            finalPrice,
            usedPoint,
            request.receiverName(),
            request.receiverPhone(),
            request.receiverAddress(),
            request.deliveryMessage(),
            deliveryFee,
            request.orderType()
        );

        Order savedOrder = orderRepository.save(order);

        List<OrderItem> orderItems = sortedTargets.stream()
            .map(target -> createOrderItem(savedOrder, target))
            .toList();

        orderItemRepository.saveAll(orderItems);

        if (request.orderType() == OrderType.CART) {
            cartItemRepository.deleteAllById(request.cartItemIds());
        }

        List<CreateOrderItemResponse> orderItemResponses = orderItems.stream()
            .map(this::toCreateOrderItemResponse)
            .toList();

        return CreateOrderResponse.of(
            savedOrder,
            orderItemResponses
        );
    }

    /**
     * 주문 취소
     * - 본인 주문 검증
     * - 이미 취소된 주문 중복 취소 방지
     * - 재고 복구
     * - 포인트/쿠폰은 MVP 이후 연동
     */
    public CancelOrderResponse cancelOrder(
        Long userId,
        Long orderId,
        CancelOrderRequest request
    ) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CustomException(ErrorCode.ORDER_007));

        validateOrderOwner(
            userId,
            order
        );

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new CustomException(ErrorCode.ORDER_008);
        }

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderId);

        // 재고 복구
        for (OrderItem orderItem : orderItems) {
            orderItem.getProductItem()
                .increaseStock(orderItem.getQuantity());
        }

        order.cancel();

        // MVP 단계에서는 쿠폰 복구 미구현이므로 null
        RestoredCouponResponse restoredCoupon = null;

        return new CancelOrderResponse(
            order.getId(),
            order.getStatus(),
            order.getFinalPrice(),
            order.getUsedPoint(),
            restoredCoupon
        );
    }

    /**
     * 주문 유형 검증
     * - DIRECT: items 필수
     * - CART: cartItemIds 필수
     */
    private void validateOrderType(CreateOrderRequest request) {
        if (request.orderType() == null) {
            throw new CustomException(ErrorCode.ORDER_001);
        }

        if (request.orderType() == OrderType.DIRECT
            && (request.items() == null || request.items().isEmpty())) {
            throw new CustomException(ErrorCode.ORDER_001);
        }

        if (request.orderType() == OrderType.CART
            && (request.cartItemIds() == null || request.cartItemIds().isEmpty())) {
            throw new CustomException(ErrorCode.ORDER_001);
        }
    }

    /**
     * 주문 유형에 따라 주문 대상 상품 목록 생성
     */
    private List<OrderTarget> getOrderTargets(
        Long userId,
        CreateOrderRequest request
    ) {
        if (request.orderType() == OrderType.DIRECT) {
            return getDirectOrderTargets(request.items());
        }

        return getCartOrderTargets(
            userId,
            request.cartItemIds()
        );
    }

    /**
     * DIRECT 주문 대상 생성
     */
    private List<OrderTarget> getDirectOrderTargets(
        List<CreateOrderItemRequest> items
    ) {
        return items.stream()
            .map(itemRequest -> {
                ProductItem productItem = productItemRepository.findById(itemRequest.itemId())
                    .orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_001));

                validateOrderableProductItem(
                    productItem,
                    itemRequest.quantity()
                );

                productItem.decreaseStock(itemRequest.quantity());

                return new OrderTarget(
                    productItem,
                    itemRequest.quantity()
                );
            })
            .toList();
    }

    /**
     * CART 주문 대상 생성
     */
    private List<OrderTarget> getCartOrderTargets(
        Long userId,
        List<Long> cartItemIds
    ) {
        List<CartItem> cartItems = cartItemRepository.findAllById(cartItemIds);

        if (cartItems.size() != cartItemIds.size()) {
            throw new CustomException(ErrorCode.CART_004);
        }

        return cartItems.stream()
            .map(cartItem -> {
                if (!cartItem.getCart()
                    .getUser()
                    .getId()
                    .equals(userId)) {
                    throw new CustomException(ErrorCode.CART_006);
                }

                ProductItem productItem = cartItem.getProductItem();

                validateOrderableProductItem(
                    productItem,
                    cartItem.getQuantity()
                );

                productItem.decreaseStock(cartItem.getQuantity());

                return new OrderTarget(
                    productItem,
                    cartItem.getQuantity()
                );
            })
            .toList();
    }

    /**
     * 주문 가능한 상품 옵션인지 검증
     */
    private void validateOrderableProductItem(
        ProductItem productItem,
        Integer quantity
    ) {
        if (!productItem.isOnSale()) {
            throw new CustomException(ErrorCode.ORDER_002);
        }

        if (productItem.getStock() < quantity) {
            throw new CustomException(ErrorCode.ORDER_003);
        }

        if (!productItem.getProduct().isApproved()) {
            throw new CustomException(ErrorCode.ORDER_004);
        }

        if (!productItem.getProduct()
            .getSeller()
            .isApproved()) {
            throw new CustomException(ErrorCode.ORDER_005);
        }
    }

    /**
     * 최소 주문 금액 검증
     */
    private void validateMinimumOrderPrice(Long totalPrice) {
        if (totalPrice < MIN_ORDER_PRICE) {
            throw new CustomException(ErrorCode.ORDER_006);
        }
    }

    /**
     * 서버 기준 총 상품 금액 계산
     */
    private Long calculateTotalPrice(
        List<OrderTarget> orderTargets
    ) {
        return orderTargets.stream()
            .mapToLong(target ->
                target.productItem().getPrice() * target.quantity()
            )
            .sum();
    }

    /**
     * 주문 상품 엔티티 생성
     */
    private OrderItem createOrderItem(
        Order order,
        OrderTarget target
    ) {
        ProductItem productItem = target.productItem();

        String itemName = productItem.getProduct().getName()
            + " (" + productItem.getOptionSummary() + ")";

        return new OrderItem(
            order,
            productItem,
            productItem.getProduct().getSeller(),
            itemName,
            target.quantity(),
            productItem.getPrice()
        );
    }

    /**
     * 주문 상품 응답 DTO 변환
     */
    private CreateOrderItemResponse toCreateOrderItemResponse(
        OrderItem orderItem
    ) {
        Long subtotal = orderItem.getPrice() * orderItem.getQuantity();

        return new CreateOrderItemResponse(
            orderItem.getId(),
            orderItem.getProductItem().getId(),
            orderItem.getItemName(),
            orderItem.getPrice(),
            orderItem.getQuantity(),
            subtotal,
            orderItem.getSeller().getShopName(),
            orderItem.getStatus()
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

    /**
     * 주문 생성 내부 계산용 DTO
     */
    private record OrderTarget(
        ProductItem productItem,
        Integer quantity
    ) {
    }
}
