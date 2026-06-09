package com.sparta.one_stop.domain.order.service;

import com.sparta.one_stop.domain.cart.entity.CartItem;
import com.sparta.one_stop.domain.cart.repository.CartItemRepository;
import com.sparta.one_stop.domain.coupon.dto.CouponDiscountResult;
import com.sparta.one_stop.domain.coupon.dto.CouponRestoreResult;
import com.sparta.one_stop.domain.coupon.service.CouponCommandService;
import com.sparta.one_stop.domain.coupon.service.CouponQueryService;
import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.delivery.entity.DeliveryHistory;
import com.sparta.one_stop.domain.delivery.repository.DeliveryHistoryRepository;
import com.sparta.one_stop.domain.delivery.repository.DeliveryRepository;
import com.sparta.one_stop.domain.order.dto.request.CancelOrderRequest;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderItemRequest;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderRequest;
import com.sparta.one_stop.domain.order.dto.response.CancelOrderResponse;
import com.sparta.one_stop.domain.order.dto.response.CreateOrderItemResponse;
import com.sparta.one_stop.domain.order.dto.response.CreateOrderResponse;
import com.sparta.one_stop.domain.order.dto.response.RestoredCouponResponse;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderCancelHistory;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderCancelHistoryRepository;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.point.service.PointService;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.domain.subscription.service.SubscriptionBenefitService;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import com.sparta.one_stop.global.enums.order.CancelActorType;
import com.sparta.one_stop.global.enums.order.OrderCancelType;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.order.OrderType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final DeliveryRepository deliveryRepository;
    private final OrderCancelHistoryRepository orderCancelHistoryRepository;
    private final DeliveryHistoryRepository deliveryHistoryRepository;
    private final PointService pointService;
    private final CouponQueryService couponQueryService;
    private final CouponCommandService couponCommandService;
    private final SubscriptionBenefitService subscriptionBenefitService;

    /**
     * 주문 생성 1단계
     * - DIRECT / CART 주문 분기
     * - 서버 기준 상품 가격 재계산
     * - 재고 검증 및 차감
     * - userCouponId가 있으면 쿠폰 검증 및 할인 금액 계산
     * - userCouponId가 없으면 쿠폰 할인 없이 주문 생성
     * - 쿠폰 할인 금액과 사용 포인트 합계가 상품 금액을 초과하지 않는지 검증
     * - Order 상태는 PENDING_PAYMENT 로 생성
     * - OrderItem 상태도 PENDING_PAYMENT 로 생성
     */
    public CreateOrderResponse createOrder(
        Long userId,
        CreateOrderRequest request
    ) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_001));

        validateOrderType(request);

        List<OrderTarget> orderTargets = prepareOrderTargets(
            userId,
            request
        );

        List<OrderTarget> sortedTargets = orderTargets.stream()
            .sorted(Comparator.comparing(target -> target.productItem().getId()))
            .toList();

        Long totalPrice = calculateTotalPrice(sortedTargets);

        Long subscriptionDiscount =
            subscriptionBenefitService.calculateDiscount(userId, totalPrice);

        subscriptionDiscount = subscriptionDiscount != null ? subscriptionDiscount : 0L;

        validateMinimumOrderPrice(totalPrice);

        CouponDiscountResult couponDiscountResult = couponQueryService.validateAndCalculateDiscount(
            userId,
            request.userCouponId(),
            totalPrice
        );

        Long discountPrice = couponDiscountResult.discountPrice();
        Integer usedPoint = request.usedPoint() != null ? request.usedPoint() : 0;

        validateDiscountAndPointLimit(
            totalPrice,
            discountPrice,
            usedPoint
        );

        Long deliveryFee = DEFAULT_DELIVERY_FEE;
        Long finalPrice = totalPrice - discountPrice - usedPoint - subscriptionDiscount + deliveryFee;

        Order order = new Order(
            user,
            couponDiscountResult.userCoupon(),
            totalPrice,
            discountPrice,
            finalPrice,
            usedPoint,
            subscriptionDiscount,
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
            .map(CreateOrderItemResponse::of)
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
     * - 배송 상태 기준 취소 가능 여부 검증
     * - 재고 복구
     * - 사용 쿠폰 복구
     * - 사용 포인트 복구
     * - Order / OrderItem / Delivery 상태 취소 처리
     * - 주문 취소 이력 저장
     * - 배송 취소 이력 저장
     */
    public CancelOrderResponse cancelOrder(
        Long userId,
        Long orderId,
        CancelOrderRequest request
    ) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CustomException(ErrorCode.ORDER_006));

        validateOrderOwner(
            userId,
            order
        );

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new CustomException(ErrorCode.ORDER_008);
        }

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdWithProductItem(orderId);

        List<Delivery> deliveries = findDeliveriesForCancel(
            order,
            orderItems
        );

        validateCancelableDeliveryStatus(
            order,
            orderItems,
            deliveries
        );

        // 재고 복구 및 주문 상품 취소 처리
        for (OrderItem orderItem : orderItems) {
            orderItem.getProductItem()
                .increaseStock(orderItem.getQuantity());

            orderItem.cancel();
        }

        // 배송 상태 ORDER_CANCELLED 처리 및 배송 이력 저장
        if (!deliveries.isEmpty()) {
            for (Delivery delivery : deliveries) {
                delivery.cancelOrder();
            }

            List<DeliveryHistory> deliveryHistories = deliveries.stream()
                .map(delivery -> new DeliveryHistory(
                    delivery,
                    DeliveryStatus.ORDER_CANCELLED
                ))
                .toList();

            deliveryHistoryRepository.saveAll(deliveryHistories);
        }

        CouponRestoreResult couponRestoreResult = couponCommandService.restoreCouponByOrder(order);

        RestoredCouponResponse restoredCoupon = RestoredCouponResponse.of(couponRestoreResult);

        order.cancel();

        Integer restoredPoint = pointService.refundPointByOrder(order);

        OrderCancelHistory cancelHistory = new OrderCancelHistory(
            order,
            null,
            CancelActorType.BUYER,
            userId,
            OrderCancelType.BUYER_CANCEL,
            request.reason(),
            order.getFinalPrice(),
            restoredPoint
        );

        orderCancelHistoryRepository.save(cancelHistory);

        return new CancelOrderResponse(
            order.getId(),
            order.getStatus(),
            order.getFinalPrice(),
            restoredPoint,
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
     * 주문 유형에 따라 주문 대상 상품을 준비
     * - DIRECT / CART 분기
     * - 주문 가능 여부 검증 및 재고 차감 포함
     */
    private List<OrderTarget> prepareOrderTargets(
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
     * - 요청 상품 옵션 ID를 item_id ASC 순서로 정렬한 뒤 비관적 락으로 일괄 조회한다.
     * - 동일한 순서로 락을 획득하여 다중 상품 주문 시 데드락 가능성을 줄인다.
     * - 주문 가능 여부 검증 후 재고를 차감한다.
     */
    private List<OrderTarget> getDirectOrderTargets(
        List<CreateOrderItemRequest> items
    ) {
        List<Long> itemIds = items.stream()
            .map(CreateOrderItemRequest::itemId)
            .distinct()
            .sorted()
            .toList();

        Map<Long, ProductItem> productItemMap = productItemRepository.findAllByIdInForUpdate(itemIds)
            .stream()
            .collect(Collectors.toMap(
                ProductItem::getId,
                Function.identity()
            ));

        return items.stream()
            .map(itemRequest -> {
                ProductItem productItem = productItemMap.get(itemRequest.itemId());

                if (productItem == null) {
                    throw new CustomException(ErrorCode.PRODUCT_001);
                }

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
     * - 장바구니 상품 조회 후 본인 장바구니인지 검증한다.
     * - CartItem의 ProductItem을 그대로 사용하지 않고, item_id ASC 순서로 비관적 락을 걸어 재조회한다.
     * - 주문 가능 여부 검증 후 재고를 차감한다.
     */
    private List<OrderTarget> getCartOrderTargets(
        Long userId,
        List<Long> cartItemIds
    ) {
        List<CartItem> cartItems = cartItemRepository.findAllById(cartItemIds);

        if (cartItems.size() != cartItemIds.size()) {
            throw new CustomException(ErrorCode.CART_004);
        }

        validateCartItemOwner(
            userId,
            cartItems
        );

        List<Long> itemIds = cartItems.stream()
            .map(cartItem -> cartItem.getProductItem().getId())
            .distinct()
            .sorted()
            .toList();

        Map<Long, ProductItem> productItemMap = productItemRepository.findAllByIdInForUpdate(itemIds)
            .stream()
            .collect(Collectors.toMap(
                ProductItem::getId,
                Function.identity()
            ));

        return cartItems.stream()
            .map(cartItem -> {
                Long itemId = cartItem.getProductItem().getId();

                ProductItem productItem = productItemMap.get(itemId);

                if (productItem == null) {
                    throw new CustomException(ErrorCode.PRODUCT_001);
                }

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
     * 장바구니 상품 소유자 검증
     * - 요청 사용자의 장바구니 상품만 주문할 수 있다.
     */
    private void validateCartItemOwner(
        Long userId,
        List<CartItem> cartItems
    ) {
        for (CartItem cartItem : cartItems) {
            if (!cartItem.getCart()
                .getUser()
                .getId()
                .equals(userId)) {
                throw new CustomException(ErrorCode.CART_006);
            }
        }
    }

    /**
     * 주문 가능한 상품 옵션인지 검증
     */
    private void validateOrderableProductItem(
        ProductItem productItem,
        Integer quantity
    ) {
        if (!productItem.isOnSale()) {
            throw new CustomException(ErrorCode.ORDER_003);
        }

        if (productItem.getStock() < quantity) {
            throw new CustomException(ErrorCode.INVENTORY_001);
        }

        if (!productItem.getProduct().isApproved()) {
            throw new CustomException(ErrorCode.PRODUCT_002);
        }

        if (!productItem.getProduct()
            .getSeller()
            .isApproved()) {
            throw new CustomException(ErrorCode.ORDER_011);
        }
    }

    /**
     * 최소 주문 금액 검증
     */
    private void validateMinimumOrderPrice(Long totalPrice) {
        if (totalPrice < MIN_ORDER_PRICE) {
            throw new CustomException(ErrorCode.ORDER_010);
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
     * - 주문 시점의 상품명/옵션명/가격/썸네일 URL을 스냅샷으로 저장
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
            productItem.getPrice(),
            productItem.getProduct().getThumbnailUrl()
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
     * 배송 상태 기준 주문 취소 가능 여부 검증
     * - 결제 전(PENDING_PAYMENT)에는 배송 정보가 없어도 취소 가능
     * - 결제 후 배송 정보가 있다면 ACCEPT, INSTRUCT 상태에서만 취소 가능
     * - 결제 후 일부 주문상품에 배송 정보가 없으면 비정상 상태로 처리
     * - DEPARTURE 이후 상태에서는 취소 불가
     */
    private void validateCancelableDeliveryStatus(
        Order order,
        List<OrderItem> orderItems,
        List<Delivery> deliveries
    ) {
        if (deliveries.isEmpty()) {
            if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                return;
            }

            throw new CustomException(ErrorCode.SHIPPING_005);
        }

        if (deliveries.size() != orderItems.size()) {
            throw new CustomException(ErrorCode.SHIPPING_005);
        }

        boolean hasNotCancelableDelivery = deliveries.stream()
            .anyMatch(delivery -> !delivery.isCancelable());

        if (hasNotCancelableDelivery) {
            throw new CustomException(ErrorCode.ORDER_008);
        }
    }

    /**
     * 취소 대상 배송 목록 조회
     * - 결제 전 주문은 배송 정보가 없을 수 있으므로 빈 목록 반환 가능
     */
    private List<Delivery> findDeliveriesForCancel(
        Order order,
        List<OrderItem> orderItems
    ) {
        List<Long> orderItemIds = orderItems.stream()
            .map(OrderItem::getId)
            .toList();

        List<Delivery> deliveries = deliveryRepository.findAllByOrderItemIdIn(
            orderItemIds
        );

        if (deliveries.isEmpty() && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            return List.of();
        }

        return deliveries;
    }

    /**
     * 쿠폰 할인 금액 + 사용 포인트 한도 검증
     * - 쿠폰 할인과 포인트 사용은 배송비를 제외한 상품 금액에만 적용
     * - 할인 금액과 사용 포인트의 합은 총 상품 금액을 초과할 수 없음
     */
    private void validateDiscountAndPointLimit(
        Long totalPrice,
        Long discountPrice,
        Integer usedPoint
    ) {
        if (discountPrice + usedPoint.longValue() > totalPrice) {
            throw new CustomException(ErrorCode.ORDER_012);
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
