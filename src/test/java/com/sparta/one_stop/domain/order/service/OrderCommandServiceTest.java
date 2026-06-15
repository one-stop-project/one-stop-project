package com.sparta.one_stop.domain.order.service;

import com.sparta.one_stop.domain.cart.entity.Cart;
import com.sparta.one_stop.domain.cart.entity.CartItem;
import com.sparta.one_stop.domain.cart.repository.CartItemRepository;
import com.sparta.one_stop.domain.coupon.dto.CouponDiscountResult;
import com.sparta.one_stop.domain.coupon.dto.CouponRestoreResult;
import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.coupon.service.CouponCommandService;
import com.sparta.one_stop.domain.coupon.service.CouponQueryService;
import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.delivery.repository.DeliveryHistoryRepository;
import com.sparta.one_stop.domain.delivery.repository.DeliveryRepository;
import com.sparta.one_stop.domain.order.dto.request.CancelOrderRequest;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderItemRequest;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderRequest;
import com.sparta.one_stop.domain.order.dto.response.CancelOrderResponse;
import com.sparta.one_stop.domain.order.dto.response.CreateOrderResponse;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderCancelHistory;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderCancelHistoryRepository;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.payment.entity.Payment;
import com.sparta.one_stop.domain.payment.repository.PaymentRepository;
import com.sparta.one_stop.domain.point.payment.PaymentPointGuard;
import com.sparta.one_stop.domain.point.service.PointService;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.domain.subscription.service.SubscriptionBenefitService;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.coupon.UserCouponStatus;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.order.OrderType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductItemRepository productItemRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private OrderCancelHistoryRepository orderCancelHistoryRepository;

    @Mock
    private DeliveryHistoryRepository deliveryHistoryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PointService pointService;

    @Mock
    private PaymentPointGuard paymentPointGuard;

    @Mock
    private CouponQueryService couponQueryService;

    @Mock
    private CouponCommandService couponCommandService;

    @Mock
    private SubscriptionBenefitService subscriptionBenefitService;

    @InjectMocks
    private OrderCommandService orderCommandService;

    @Test
    @DisplayName("createOrder 성공 - DIRECT 주문을 생성하고 재고를 차감한다")
    void createOrder_success_directOrder() {
        // given
        Long userId = 1L;
        Long itemId = 101L;

        User user = mock(User.class);
        ProductItem productItem = orderableProductItem(
            itemId,
            10000L,
            10L
        );

        given(couponQueryService.validateAndCalculateDiscount(
            anyLong(),
            any(),
            anyLong()
        )).willReturn(CouponDiscountResult.none());

        CreateOrderRequest request = directOrderRequest(
            itemId,
            2
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        mockProductItemsWithLock(
            List.of(itemId),
            List.of(productItem)
        );

        when(orderRepository.save(any(Order.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CreateOrderResponse result = orderCommandService.createOrder(
            userId,
            request
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.deliveryFee()).isEqualTo(3000L);
        assertThat(result.finalPrice()).isEqualTo(23000L);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getUser()).isSameAs(user);
        assertThat(savedOrder.getTotalPrice()).isEqualTo(20000L);
        assertThat(savedOrder.getDiscountPrice()).isEqualTo(0L);
        assertThat(savedOrder.getUsedPoint()).isEqualTo(0);
        assertThat(savedOrder.getDeliveryFee()).isEqualTo(3000L);
        assertThat(savedOrder.getFinalPrice()).isEqualTo(23000L);
        assertThat(savedOrder.getOrderType()).isEqualTo(OrderType.DIRECT);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        verify(productItem).decreaseStock(2);

        ArgumentCaptor<List<OrderItem>> orderItemsCaptor =
            ArgumentCaptor.forClass(List.class);

        verify(orderItemRepository).saveAll(orderItemsCaptor.capture());

        List<OrderItem> orderItems = orderItemsCaptor.getValue();

        assertThat(orderItems).hasSize(1);
        assertThat(orderItems.get(0).getOrder()).isSameAs(savedOrder);
        assertThat(orderItems.get(0).getProductItem()).isSameAs(productItem);
        assertThat(orderItems.get(0).getQuantity()).isEqualTo(2);
        assertThat(orderItems.get(0).getPrice()).isEqualTo(10000L);

        verify(cartItemRepository, never()).deleteAllById(any());
    }

    @Test
    @DisplayName("createOrder 성공 - 구독 할인이 적용된 최종 금액을 계산한다")
    void createOrder_success_withSubscriptionDiscount() {
        // 1. Given: 데이터 준비
        Long userId = 1L;
        Long itemId = 101L;
        User user = mock(User.class);

        // 10,000원짜리 상품 2개 = 총 20,000원
        ProductItem productItem = orderableProductItem(
            itemId,
            10000L,
            10L
        );
        CreateOrderRequest request = directOrderRequest(itemId, 2);

        // Mock 설정
        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        mockProductItemsWithLock(
            List.of(itemId),
            List.of(productItem)
        );

        // 쿠폰 할인은 0원 (순수 구독 할인만 테스트하기 위함)
        given(couponQueryService.validateAndCalculateDiscount(anyLong(), any(), anyLong()))
            .willReturn(CouponDiscountResult.none());

        // 구독 할인 5% (20,000 * 0.05 = 1,000원) 설정
        given(subscriptionBenefitService.calculateDiscount(userId, 20000L))
            .willReturn(1000L);

        when(orderRepository.save(any(Order.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // 2. When: 주문 생성 실행
        CreateOrderResponse result = orderCommandService.createOrder(userId, request);

        // 3. Then: 검증
        // 계산식: 20,000(상품가) - 0(쿠폰) - 0(포인트) - 1,000(구독할인) + 3,000(배송비) = 22,000
        assertThat(result.finalPrice()).isEqualTo(22000L);

        // 엔티티에 구독 할인 금액이 잘 저장되었는지 확인
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getSubscriptionDiscount()).isEqualTo(1000L);
        assertThat(savedOrder.getFinalPrice()).isEqualTo(22000L);
    }

    @Test
    @DisplayName("createOrder 성공 - 구독자는 무료 배송이 적용된다")
    void createOrder_success_withFreeShipping() {
        // given
        Long userId = 1L;
        Long itemId = 101L;
        User user = mock(User.class);
        ProductItem productItem = orderableProductItem(itemId, 10000L, 10L); // 10,000 x 2 = 20,000

        CreateOrderRequest request = directOrderRequest(itemId, 2);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        mockProductItemsWithLock(List.of(itemId), List.of(productItem));
        given(couponQueryService.validateAndCalculateDiscount(anyLong(), any(), anyLong()))
            .willReturn(CouponDiscountResult.none());

        // 구독자: 5% 할인 + 무료배송
        given(subscriptionBenefitService.calculateDiscount(userId, 20000L)).willReturn(1000L);
        given(subscriptionBenefitService.isFreeShippingEligible(userId)).willReturn(true);

        when(orderRepository.save(any(Order.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CreateOrderResponse result = orderCommandService.createOrder(userId, request);

        // then
        // 20,000(상품) - 0(쿠폰) - 0(포인트) - 1,000(구독할인) + 0(무료배송) = 19,000
        assertThat(result.deliveryFee()).isEqualTo(0L);
        assertThat(result.finalPrice()).isEqualTo(19000L);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getDeliveryFee()).isEqualTo(0L);
        assertThat(savedOrder.getSubscriptionDiscount()).isEqualTo(1000L);
        assertThat(savedOrder.getFinalPrice()).isEqualTo(19000L);
    }


    @Test
    @DisplayName("createOrder 성공 - CART 주문을 생성하고 cartItem을 삭제한다")
    void createOrder_success_cartOrder() {
        // given
        Long userId = 1L;
        Long cartItemId = 10L;
        Long itemId = 101L;

        User user = mock(User.class);
        ProductItem productItem = orderableProductItem(
            itemId,
            10000L,
            10L
        );
        CartItem cartItem = cartItem(
            userId,
            productItem,
            2
        );

        given(couponQueryService.validateAndCalculateDiscount(
            anyLong(),
            any(),
            anyLong()
        )).willReturn(CouponDiscountResult.none());

        CreateOrderRequest request = cartOrderRequest(
            cartItemId
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(cartItemRepository.findAllById(List.of(cartItemId)))
            .thenReturn(List.of(cartItem));

        mockProductItemsWithLock(
            List.of(itemId),
            List.of(productItem)
        );

        when(orderRepository.save(any(Order.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CreateOrderResponse result = orderCommandService.createOrder(
            userId,
            request
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.deliveryFee()).isEqualTo(3000L);
        assertThat(result.finalPrice()).isEqualTo(23000L);

        verify(productItem).decreaseStock(2);
        verify(orderRepository).save(any(Order.class));
        verify(orderItemRepository).saveAll(any());
        verify(cartItemRepository).deleteAllById(List.of(cartItemId));
    }

    @Test
    @DisplayName("createOrder 성공 - usedPoint가 있으면 포인트 사전 검증을 수행한다")
    void createOrder_success_whenUsedPointExists_verifyPaymentPointGuardCalled() {
        // given
        Long userId = 1L;
        Long itemId = 101L;

        User user = mock(User.class);
        ProductItem productItem = orderableProductItem(
            itemId,
            10000L,
            10L
        );

        CreateOrderRequest request = new CreateOrderRequest(
            OrderType.DIRECT,
            List.of(new CreateOrderItemRequest(
                itemId,
                1
            )),
            null,
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞",
            null,
            5000
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        mockProductItemsWithLock(
            List.of(itemId),
            List.of(productItem)
        );

        given(couponQueryService.validateAndCalculateDiscount(
            anyLong(),
            any(),
            anyLong()
        )).willReturn(CouponDiscountResult.none());

        when(orderRepository.save(any(Order.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CreateOrderResponse result = orderCommandService.createOrder(
            userId,
            request
        );

        // then
        assertThat(result).isNotNull();
        assertThat(result.deliveryFee()).isEqualTo(3000L);
        assertThat(result.finalPrice()).isEqualTo(8000L);

        verify(paymentPointGuard).validateOnOrderCreation(
            userId,
            5000
        );

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getTotalPrice()).isEqualTo(10000L);
        assertThat(savedOrder.getDiscountPrice()).isEqualTo(0L);
        assertThat(savedOrder.getUsedPoint()).isEqualTo(5000);
        assertThat(savedOrder.getDeliveryFee()).isEqualTo(3000L);
        assertThat(savedOrder.getFinalPrice()).isEqualTo(8000L);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        verify(productItem).decreaseStock(1);
        verify(orderItemRepository).saveAll(any());
    }

    @Test
    @DisplayName("createOrder 성공 - 쿠폰 할인이 최종 결제 금액에 반영된다")
    void createOrder_success_whenCouponDiscountApplied() {
        // given
        Long userId = 1L;
        Long itemId = 101L;
        Long userCouponId = 100L;

        User user = mock(User.class);
        UserCoupon userCoupon = mock(UserCoupon.class);

        ProductItem productItem = orderableProductItem(
            itemId,
            10000L,
            10L
        );

        CreateOrderRequest request = new CreateOrderRequest(
            OrderType.DIRECT,
            List.of(new CreateOrderItemRequest(
                itemId,
                2
            )),
            null,
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞",
            userCouponId,
            0
        );

        CouponDiscountResult couponDiscountResult = mock(CouponDiscountResult.class);

        when(couponDiscountResult.discountPrice())
            .thenReturn(5000L);
        when(couponDiscountResult.userCoupon())
            .thenReturn(userCoupon);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        mockProductItemsWithLock(
            List.of(itemId),
            List.of(productItem)
        );

        given(couponQueryService.validateAndCalculateDiscount(
            userId,
            userCouponId,
            20000L
        )).willReturn(couponDiscountResult);

        when(orderRepository.save(any(Order.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        CreateOrderResponse result = orderCommandService.createOrder(
            userId,
            request
        );

        // then
        // 20,000(상품) - 5,000(쿠폰) - 0(포인트) - 0(구독할인) + 3,000(배송비) = 18,000
        assertThat(result).isNotNull();
        assertThat(result.deliveryFee()).isEqualTo(3000L);
        assertThat(result.finalPrice()).isEqualTo(18000L);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getUser()).isSameAs(user);
        assertThat(savedOrder.getUserCoupon()).isSameAs(userCoupon);
        assertThat(savedOrder.getTotalPrice()).isEqualTo(20000L);
        assertThat(savedOrder.getDiscountPrice()).isEqualTo(5000L);
        assertThat(savedOrder.getUsedPoint()).isEqualTo(0);
        assertThat(savedOrder.getDeliveryFee()).isEqualTo(3000L);
        assertThat(savedOrder.getFinalPrice()).isEqualTo(18000L);
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        verify(couponQueryService).validateAndCalculateDiscount(
            userId,
            userCouponId,
            20000L
        );

        verify(productItem).decreaseStock(2);
        verify(orderItemRepository).saveAll(any());
    }

    @Test
    @DisplayName("cancelOrder 성공 - 쿠폰 복구 결과가 있으면 응답에 복구 쿠폰 정보를 포함한다")
    void cancelOrder_success_whenCouponRestoreResultExists() {
        // given
        Long userId = 1L;
        Long orderId = 10L;
        Long userCouponId = 100L;

        AtomicReference<OrderStatus> orderStatus =
            new AtomicReference<>(OrderStatus.PAID);

        Order order = cancelableOrder(
            orderId,
            userId,
            18000L,
            orderStatus
        );

        ProductItem productItem = mock(ProductItem.class);
        when(productItem.getId()).thenReturn(101L);

        OrderItem orderItem = cancelableOrderItem(
            101L,
            productItem,
            2
        );

        Delivery delivery = mock(Delivery.class);
        Payment payment = mock(Payment.class);

        CouponRestoreResult couponRestoreResult = mock(CouponRestoreResult.class);

        when(couponRestoreResult.userCouponId())
            .thenReturn(userCouponId);
        when(couponRestoreResult.couponName())
            .thenReturn("테스트 쿠폰");
        when(couponRestoreResult.status())
            .thenReturn(UserCouponStatus.AVAILABLE);

        CancelOrderRequest request = new CancelOrderRequest("단순 변심");

        mockOrderWithLock(orderId, order);

        when(orderItemRepository.findAllByOrderIdWithProductItem(orderId))
            .thenReturn(List.of(orderItem));

        when(deliveryRepository.findAllByOrderItemIdIn(List.of(101L)))
            .thenReturn(List.of(delivery));

        when(delivery.isCancelable())
            .thenReturn(true);

        when(paymentRepository.findByOrderId(orderId))
            .thenReturn(Optional.of(payment));

        when(couponCommandService.restoreCouponByOrder(order))
            .thenReturn(couponRestoreResult);

        when(pointService.refundPointByOrder(order))
            .thenReturn(0);

        // when
        CancelOrderResponse result = orderCommandService.cancelOrder(
            userId,
            orderId,
            request
        );

        // then
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.refundAmount()).isEqualTo(18000L);
        assertThat(result.restoredPoint()).isEqualTo(0);
        assertThat(result.restoredCoupon()).isNotNull();

        assertThat(result.restoredCoupon().userCouponId()).isEqualTo(userCouponId);
        assertThat(result.restoredCoupon().couponName()).isEqualTo("테스트 쿠폰");
        assertThat(result.restoredCoupon().status()).isEqualTo(UserCouponStatus.AVAILABLE);

        verify(productItemRepository).increaseStockById(
            101L,
            2
        );
        verify(productItem, never()).increaseStock(anyLong());

        verify(couponCommandService).restoreCouponByOrder(order);

        verify(paymentRepository).findByOrderId(orderId);
        verify(payment).cancel();

        verify(orderItem).cancel();
        verify(delivery).cancelOrder();
        verify(order).cancel();
        verify(pointService).refundPointByOrder(order);
        verify(orderCancelHistoryRepository).save(any(OrderCancelHistory.class));
        verify(deliveryHistoryRepository).saveAll(any());
    }

    @Test
    @DisplayName("createOrder 실패 - orderType이 null이면 예외 발생")
    void createOrder_fail_whenOrderTypeIsNull() {
        // given
        Long userId = 1L;
        User user = mock(User.class);

        CreateOrderRequest request = new CreateOrderRequest(
            null,
            null,
            null,
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞",
            null,
            0
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            userId,
            request
        )).isInstanceOf(CustomException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder 실패 - DIRECT 주문인데 items가 없으면 예외 발생")
    void createOrder_fail_whenDirectItemsAreEmpty() {
        // given
        Long userId = 1L;
        User user = mock(User.class);

        CreateOrderRequest request = new CreateOrderRequest(
            OrderType.DIRECT,
            List.of(),
            null,
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞",
            null,
            0
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            userId,
            request
        )).isInstanceOf(CustomException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder 실패 - CART 주문인데 cartItemIds가 없으면 예외 발생")
    void createOrder_fail_whenCartItemIdsAreEmpty() {
        // given
        Long userId = 1L;
        User user = mock(User.class);

        CreateOrderRequest request = new CreateOrderRequest(
            OrderType.CART,
            null,
            List.of(),
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞",
            null,
            0
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            userId,
            request
        )).isInstanceOf(CustomException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder 실패 - 존재하지 않는 상품 옵션이면 예외 발생")
    void createOrder_fail_whenProductItemDoesNotExist() {
        // given
        Long userId = 1L;
        Long itemId = 101L;
        User user = mock(User.class);

        CreateOrderRequest request = directOrderRequest(
            itemId,
            1
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        mockProductItemsWithLock(
            List.of(itemId),
            List.of()
        );

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            userId,
            request
        )).isInstanceOf(CustomException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder 실패 - STOP 상품이면 예외 발생")
    void createOrder_fail_whenProductItemIsStopped() {
        // given
        Long userId = 1L;
        Long itemId = 101L;
        User user = mock(User.class);
        ProductItem productItem = productItemOnlyOnSale(
            itemId,
            false
        );

        CreateOrderRequest request = directOrderRequest(
            itemId,
            1
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        mockProductItemsWithLock(
            List.of(itemId),
            List.of(productItem)
        );

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            userId,
            request
        )).isInstanceOf(CustomException.class);

        verify(productItem, never()).decreaseStock(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder 실패 - 재고 부족이면 예외 발생")
    void createOrder_fail_whenStockIsInsufficient() {
        // given
        Long userId = 1L;
        Long itemId = 101L;
        User user = mock(User.class);
        ProductItem productItem = productItemForStockValidation(
            itemId,
            true,
            1L
        );

        CreateOrderRequest request = directOrderRequest(
            itemId,
            2
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        mockProductItemsWithLock(
            List.of(itemId),
            List.of(productItem)
        );

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            userId,
            request
        )).isInstanceOf(CustomException.class);

        verify(productItem, never()).decreaseStock(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder 실패 - 존재하지 않는 cartItem이 포함되면 예외 발생")
    void createOrder_fail_whenCartItemDoesNotExist() {
        // given
        Long userId = 1L;
        Long cartItemId = 10L;
        User user = mock(User.class);

        CreateOrderRequest request = cartOrderRequest(
            cartItemId
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));
        when(cartItemRepository.findAllById(List.of(cartItemId)))
            .thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            userId,
            request
        )).isInstanceOf(CustomException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder 실패 - 본인 장바구니 상품이 아니면 예외 발생")
    void createOrder_fail_whenCartItemOwnerMismatch() {
        // given
        Long requestUserId = 1L;
        Long cartOwnerId = 2L;
        Long cartItemId = 10L;

        User user = mock(User.class);
        CartItem cartItem = cartItemOwnerOnly(cartOwnerId);

        CreateOrderRequest request = cartOrderRequest(cartItemId);

        when(userRepository.findById(requestUserId))
            .thenReturn(Optional.of(user));
        when(cartItemRepository.findAllById(List.of(cartItemId)))
            .thenReturn(List.of(cartItem));

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            requestUserId,
            request
        )).isInstanceOf(CustomException.class);

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder 실패 - CART인데 DB에 cartItem 자체가 없음")
    void createOrder_fail_whenCartItemsNotFoundInDb() {
        Long userId = 1L;
        User user = mock(User.class);

        CreateOrderRequest request = cartOrderRequest(10L);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cartItemRepository.findAllById(List.of(10L)))
            .thenReturn(List.of()); // 없음

        assertThatThrownBy(() ->
            orderCommandService.createOrder(userId, request)
        ).isInstanceOf(CustomException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder 실패 - 쿠폰할인, 포인트, 구독할인 합계가 상품 금액을 초과하면 예외 발생")
    void createOrder_fail_whenDiscountPointAndSubscriptionDiscountExceedTotalPrice() {
        // given
        Long userId = 1L;
        Long itemId = 101L;

        User user = mock(User.class);

        // 상품 금액: 10,000원
        ProductItem productItem = productItemForDiscountLimitValidation(
            itemId,
            10000L,
            10L
        );

        // 사용 포인트: 10,000원
        CreateOrderRequest request = new CreateOrderRequest(
            OrderType.DIRECT,
            List.of(new CreateOrderItemRequest(
                itemId,
                1
            )),
            null,
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞",
            null,
            10000
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        mockProductItemsWithLock(
            List.of(itemId),
            List.of(productItem)
        );

        given(couponQueryService.validateAndCalculateDiscount(
            anyLong(),
            any(),
            anyLong()
        )).willReturn(CouponDiscountResult.none());

        // 구독 할인: 4,000원
        // 기존 로직이면 totalPrice 10,000 - usedPoint 10,000 - subscriptionDiscount 4,000 + deliveryFee 3,000 = -1,000
        given(subscriptionBenefitService.calculateDiscount(userId, 10000L))
            .willReturn(4000L);

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORDER_012);

        verify(orderRepository, never()).save(any(Order.class));
        verify(orderItemRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("createOrder 실패 - 최소 주문 금액 1,000원 미만이면 예외 발생")
    void createOrder_fail_whenTotalPriceIsLessThanMinimumOrderPrice() {
        // given
        Long userId = 1L;
        Long itemId = 101L;

        User user = mock(User.class);
        ProductItem productItem = productItemForDiscountLimitValidation(
            itemId,
            500L,
            10L
        );

        CreateOrderRequest request = directOrderRequest(
            itemId,
            1
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        mockProductItemsWithLock(
            List.of(itemId),
            List.of(productItem)
        );

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORDER_010);

        verify(orderRepository, never()).save(any(Order.class));
        verify(orderItemRepository, never()).saveAll(any());
        verify(couponQueryService, never()).validateAndCalculateDiscount(
            anyLong(),
            any(),
            anyLong()
        );
        verify(paymentPointGuard, never()).validateOnOrderCreation(
            anyLong(),
            any()
        );
    }

    @Test
    @DisplayName("createOrder 실패 - usedPoint가 있을 때 포인트 검증 실패 시 예외 발생")
    void createOrder_fail_whenPaymentPointGuardThrowsException() {
        // given
        Long userId = 1L;
        Long itemId = 101L;

        User user = mock(User.class);
        ProductItem productItem = productItemForDiscountLimitValidation(
            itemId,
            10000L,
            10L
        );

        CreateOrderRequest request = new CreateOrderRequest(
            OrderType.DIRECT,
            List.of(new CreateOrderItemRequest(
                itemId,
                1
            )),
            null,
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞",
            null,
            5000
        );

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        mockProductItemsWithLock(
            List.of(itemId),
            List.of(productItem)
        );

        given(couponQueryService.validateAndCalculateDiscount(
            anyLong(),
            any(),
            anyLong()
        )).willReturn(CouponDiscountResult.none());

        CustomException pointException = new CustomException(ErrorCode.POINT_002);

        doThrow(pointException)
            .when(paymentPointGuard)
            .validateOnOrderCreation(
                userId,
                5000
            );

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            userId,
            request
        ))
            .isSameAs(pointException);

        verify(paymentPointGuard).validateOnOrderCreation(
            userId,
            5000
        );
        verify(orderRepository, never()).save(any(Order.class));
        verify(orderItemRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("createOrder 실패 - CART 주문에서 상품 옵션이 STOP 상태이면 예외 발생")
    void createOrder_fail_whenCartOrderProductItemIsStopped() {
        // given
        Long userId = 1L;
        Long cartItemId = 10L;
        Long itemId = 101L;

        User user = mock(User.class);
        ProductItem productItem = productItemOnlyOnSale(
            itemId,
            false
        );

        CartItem cartItem = cartItem(
            userId,
            productItem,
            1
        );

        CreateOrderRequest request = cartOrderRequest(cartItemId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(cartItemRepository.findAllById(List.of(cartItemId)))
            .thenReturn(List.of(cartItem));

        mockProductItemsWithLock(
            List.of(itemId),
            List.of(productItem)
        );

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORDER_003);

        verify(productItem, never()).decreaseStock(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
        verify(orderItemRepository, never()).saveAll(any());
        verify(cartItemRepository, never()).deleteAllById(any());
    }

    @Test
    @DisplayName("createOrder 실패 - CART 주문에서 상품 옵션 재고가 0이면 예외 발생")
    void createOrder_fail_whenCartOrderProductItemStockIsZero() {
        // given
        Long userId = 1L;
        Long cartItemId = 10L;
        Long itemId = 101L;

        User user = mock(User.class);
        ProductItem productItem = productItemForStockValidation(
            itemId,
            true,
            0L
        );

        CartItem cartItem = cartItem(
            userId,
            productItem,
            1
        );

        CreateOrderRequest request = cartOrderRequest(cartItemId);

        when(userRepository.findById(userId))
            .thenReturn(Optional.of(user));

        when(cartItemRepository.findAllById(List.of(cartItemId)))
            .thenReturn(List.of(cartItem));

        mockProductItemsWithLock(
            List.of(itemId),
            List.of(productItem)
        );

        // when & then
        assertThatThrownBy(() -> orderCommandService.createOrder(
            userId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVENTORY_001);

        verify(productItem, never()).decreaseStock(anyLong());
        verify(orderRepository, never()).save(any(Order.class));
        verify(orderItemRepository, never()).saveAll(any());
        verify(cartItemRepository, never()).deleteAllById(any());
    }

    @Test
    @DisplayName("cancelOrder 성공 - 결제 전 주문을 취소한다")
    void cancelOrder_success_whenPendingPaymentOrder() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        AtomicReference<OrderStatus> orderStatus =
            new AtomicReference<>(OrderStatus.PENDING_PAYMENT);

        Order order = cancelableOrder(
            orderId,
            userId,
            23000L,
            orderStatus
        );

        ProductItem productItem = mock(ProductItem.class);
        when(productItem.getId()).thenReturn(101L);

        OrderItem orderItem = cancelableOrderItem(
            101L,
            productItem,
            2
        );

        CancelOrderRequest request = new CancelOrderRequest("단순 변심");

        mockOrderWithLock(orderId, order);

        when(orderItemRepository.findAllByOrderIdWithProductItem(orderId))
            .thenReturn(List.of(orderItem));
        when(deliveryRepository.findAllByOrderItemIdIn(List.of(101L)))
            .thenReturn(List.of());
        when(couponCommandService.restoreCouponByOrder(order))
            .thenReturn(null);
        when(pointService.refundPointByOrder(order))
            .thenReturn(0);

        // when
        CancelOrderResponse result = orderCommandService.cancelOrder(
            userId,
            orderId,
            request
        );

        // then
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.refundAmount()).isEqualTo(23000L);
        assertThat(result.restoredPoint()).isEqualTo(0);

        verify(productItemRepository).increaseStockById(
            101L,
            2
        );
        verify(productItem, never()).increaseStock(anyLong());
        verify(orderItem).cancel();
        verify(order).cancel();
        verify(pointService).refundPointByOrder(order);

        ArgumentCaptor<OrderCancelHistory> historyCaptor =
            ArgumentCaptor.forClass(OrderCancelHistory.class);

        verify(orderCancelHistoryRepository).save(historyCaptor.capture());

        OrderCancelHistory history = historyCaptor.getValue();

        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getReason()).isEqualTo("단순 변심");
        assertThat(history.getCancelledPrice()).isEqualTo(23000L);

        verify(deliveryHistoryRepository, never()).saveAll(any());
        verify(paymentRepository, never()).findByOrderId(orderId);
    }

    @Test
    @DisplayName("cancelOrder 성공 - 취소 가능한 배송이 있으면 배송 취소와 배송 이력을 저장한다")
    void cancelOrder_success_whenCancelableDeliveryExists() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        AtomicReference<OrderStatus> orderStatus =
            new AtomicReference<>(OrderStatus.PAID);

        Order order = cancelableOrder(
            orderId,
            userId,
            23000L,
            orderStatus
        );

        ProductItem productItem = mock(ProductItem.class);
        when(productItem.getId()).thenReturn(101L);

        OrderItem orderItem = cancelableOrderItem(
            101L,
            productItem,
            2
        );
        Delivery delivery = mock(Delivery.class);
        Payment payment = mock(Payment.class);

        CancelOrderRequest request = new CancelOrderRequest("단순 변심");

        mockOrderWithLock(orderId, order);

        when(orderItemRepository.findAllByOrderIdWithProductItem(orderId))
            .thenReturn(List.of(orderItem));
        when(deliveryRepository.findAllByOrderItemIdIn(List.of(101L)))
            .thenReturn(List.of(delivery));
        when(delivery.isCancelable())
            .thenReturn(true);
        when(couponCommandService.restoreCouponByOrder(order))
            .thenReturn(null);
        when(pointService.refundPointByOrder(order))
            .thenReturn(0);
        when(paymentRepository.findByOrderId(orderId))
            .thenReturn(Optional.of(payment));

        // when
        CancelOrderResponse result = orderCommandService.cancelOrder(
            userId,
            orderId,
            request
        );

        // then
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);

        verify(productItemRepository).increaseStockById(
            101L,
            2
        );
        verify(productItem, never()).increaseStock(anyLong());
        verify(orderItem).cancel();
        verify(delivery).cancelOrder();
        verify(paymentRepository).findByOrderId(orderId);
        verify(payment).cancel();
        verify(order).cancel();
        verify(pointService).refundPointByOrder(order);
        verify(orderCancelHistoryRepository).save(any(OrderCancelHistory.class));
        verify(deliveryHistoryRepository).saveAll(any());
    }

    @Test
    @DisplayName("cancelOrder 성공 - 포인트 환불 금액이 있으면 응답에 복구 포인트를 포함한다")
    void cancelOrder_success_whenRefundPointExists() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        AtomicReference<OrderStatus> orderStatus =
            new AtomicReference<>(OrderStatus.PENDING_PAYMENT);

        Order order = cancelableOrder(
            orderId,
            userId,
            23000L,
            orderStatus
        );

        ProductItem productItem = mock(ProductItem.class);
        when(productItem.getId()).thenReturn(101L);

        OrderItem orderItem = cancelableOrderItem(
            101L,
            productItem,
            2
        );

        CancelOrderRequest request = new CancelOrderRequest("단순 변심");

        mockOrderWithLock(orderId, order);

        when(orderItemRepository.findAllByOrderIdWithProductItem(orderId))
            .thenReturn(List.of(orderItem));

        when(deliveryRepository.findAllByOrderItemIdIn(List.of(101L)))
            .thenReturn(List.of());

        when(couponCommandService.restoreCouponByOrder(order))
            .thenReturn(null);

        when(pointService.refundPointByOrder(order))
            .thenReturn(5000);

        // when
        CancelOrderResponse result = orderCommandService.cancelOrder(
            userId,
            orderId,
            request
        );

        // then
        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.refundAmount()).isEqualTo(23000L);
        assertThat(result.restoredPoint()).isEqualTo(5000);

        verify(productItemRepository).increaseStockById(
            101L,
            2
        );
        verify(productItem, never()).increaseStock(anyLong());
        verify(orderItem).cancel();
        verify(order).cancel();
        verify(pointService).refundPointByOrder(order);

        ArgumentCaptor<OrderCancelHistory> historyCaptor =
            ArgumentCaptor.forClass(OrderCancelHistory.class);

        verify(orderCancelHistoryRepository).save(historyCaptor.capture());

        OrderCancelHistory history = historyCaptor.getValue();

        assertThat(history.getOrder()).isSameAs(order);
        assertThat(history.getReason()).isEqualTo("단순 변심");
        assertThat(history.getCancelledPrice()).isEqualTo(23000L);

        verify(deliveryHistoryRepository, never()).saveAll(any());
        verify(paymentRepository, never()).findByOrderId(orderId);
    }

    @Test
    @DisplayName("cancelOrder 실패 - PAID인데 배송조회 결과 null이면 예외")
    void cancelOrder_fail_whenPaidOrderDeliveryMissing() {
        Long userId = 1L;
        Long orderId = 10L;

        Order order = orderForStatusValidation(userId, OrderStatus.PAID);

        OrderItem orderItem = orderItemOnlyId(101L);

        mockOrderWithLock(orderId, order);

        when(orderItemRepository.findAllByOrderIdWithProductItem(orderId))
            .thenReturn(List.of(orderItem));

        when(deliveryRepository.findAllByOrderItemIdIn(List.of(101L)))
            .thenReturn(List.of()); // 배송 없음

        assertThatThrownBy(() ->
            orderCommandService.cancelOrder(userId, orderId, new CancelOrderRequest("test"))
        ).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("cancelOrder 실패 - PAID 주문의 결제 정보가 없으면 예외 발생")
    void cancelOrder_fail_whenPaidOrderPaymentDoesNotExist() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        Order order = orderForStatusAndIdValidation(
            orderId,
            userId,
            OrderStatus.PAID
        );

        OrderItem orderItem = orderItemOnlyId(101L);
        Delivery delivery = mock(Delivery.class);
        CancelOrderRequest request = new CancelOrderRequest("단순 변심");

        mockOrderWithLock(orderId, order);

        when(orderItemRepository.findAllByOrderIdWithProductItem(orderId))
            .thenReturn(List.of(orderItem));
        when(deliveryRepository.findAllByOrderItemIdIn(List.of(101L)))
            .thenReturn(List.of(delivery));
        when(delivery.isCancelable())
            .thenReturn(true);
        when(paymentRepository.findByOrderId(orderId))
            .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderCommandService.cancelOrder(
            userId,
            orderId,
            request
        ))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.PAYMENT_009);

        verify(paymentRepository).findByOrderId(orderId);
        verify(orderCancelHistoryRepository, never()).save(any());
        verify(pointService, never()).refundPointByOrder(any());
    }

    @Test
    @DisplayName("cancelOrder 실패 - 존재하지 않는 주문이면 예외 발생")
    void cancelOrder_fail_whenOrderDoesNotExist() {
        // given
        Long userId = 1L;
        Long orderId = 10L;
        CancelOrderRequest request = new CancelOrderRequest("단순 변심");

        mockOrderNotFoundWithLock(orderId);

        // when & then
        assertThatThrownBy(() -> orderCommandService.cancelOrder(
            userId,
            orderId,
            request
        )).isInstanceOf(CustomException.class);

        verify(orderCancelHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelOrder 실패 - 본인 주문이 아니면 예외 발생")
    void cancelOrder_fail_whenOrderOwnerMismatch() {
        // given
        Long requestUserId = 1L;
        Long orderOwnerId = 2L;
        Long orderId = 10L;

        Order order = orderForOwnerValidation(orderOwnerId);
        CancelOrderRequest request = new CancelOrderRequest("단순 변심");

        mockOrderWithLock(orderId, order);

        // when & then
        assertThatThrownBy(() -> orderCommandService.cancelOrder(
            requestUserId,
            orderId,
            request
        )).isInstanceOf(CustomException.class);

        verify(orderItemRepository, never()).findAllByOrderId(orderId);
        verify(orderCancelHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelOrder 실패 - 이미 취소된 주문이면 예외 발생")
    void cancelOrder_fail_whenOrderAlreadyCancelled() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        Order order = orderForStatusValidation(
            userId,
            OrderStatus.CANCELLED
        );
        CancelOrderRequest request = new CancelOrderRequest("단순 변심");

        mockOrderWithLock(orderId, order);

        // when & then
        assertThatThrownBy(() -> orderCommandService.cancelOrder(
            userId,
            orderId,
            request
        )).isInstanceOf(CustomException.class);

        verify(orderItemRepository, never()).findAllByOrderId(orderId);
        verify(orderCancelHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelOrder 실패 - 배송 정보가 필요한 상태인데 배송 정보가 없으면 예외 발생")
    void cancelOrder_fail_whenPaidOrderHasNoDelivery() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        Order order = orderForStatusValidation(
            userId,
            OrderStatus.PAID
        );

        OrderItem orderItem = orderItemOnlyId(101L);
        CancelOrderRequest request = new CancelOrderRequest("단순 변심");

        mockOrderWithLock(orderId, order);

        when(orderItemRepository.findAllByOrderIdWithProductItem(orderId))
            .thenReturn(List.of(orderItem));
        when(deliveryRepository.findAllByOrderItemIdIn(List.of(101L)))
            .thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> orderCommandService.cancelOrder(
            userId,
            orderId,
            request
        )).isInstanceOf(CustomException.class);

        verify(orderCancelHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelOrder 실패 - 취소 불가능한 배송 상태이면 예외 발생")
    void cancelOrder_fail_whenDeliveryIsNotCancelable() {
        // given
        Long userId = 1L;
        Long orderId = 10L;

        Order order = orderForStatusValidation(
            userId,
            OrderStatus.PAID
        );

        OrderItem orderItem = orderItemOnlyId(101L);
        Delivery delivery = mock(Delivery.class);
        CancelOrderRequest request = new CancelOrderRequest("단순 변심");

        mockOrderWithLock(orderId, order);

        when(orderItemRepository.findAllByOrderIdWithProductItem(orderId))
            .thenReturn(List.of(orderItem));
        when(deliveryRepository.findAllByOrderItemIdIn(List.of(101L)))
            .thenReturn(List.of(delivery));
        when(delivery.isCancelable())
            .thenReturn(false);

        // when & then
        assertThatThrownBy(() -> orderCommandService.cancelOrder(
            userId,
            orderId,
            request
        )).isInstanceOf(CustomException.class);

        verify(orderCancelHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelOrder 실패 - 주문 락 획득 실패 시 예외 발생")
    void cancelOrder_fail_whenOrderLockTimeout() {
        // given
        Long userId = 1L;
        Long orderId = 10L;
        CancelOrderRequest request = new CancelOrderRequest("단순 변심");

        when(orderRepository.findByIdWithLock(orderId))
            .thenThrow(new PessimisticLockingFailureException("lock timeout"));

        // when & then
        assertThatThrownBy(() -> orderCommandService.cancelOrder(
            userId,
            orderId,
            request
        )).isInstanceOf(PessimisticLockingFailureException.class);

        verify(orderCancelHistoryRepository, never()).save(any());
    }

    private CreateOrderRequest directOrderRequest(
        Long itemId,
        Integer quantity
    ) {
        return new CreateOrderRequest(
            OrderType.DIRECT,
            List.of(new CreateOrderItemRequest(
                itemId,
                quantity
            )),
            null,
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞",
            null,
            0
        );
    }

    private CreateOrderRequest cartOrderRequest(Long cartItemId) {
        return new CreateOrderRequest(
            OrderType.CART,
            null,
            List.of(cartItemId),
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞",
            null,
            0
        );
    }

    private ProductItem orderableProductItem(
        Long itemId,
        Long price,
        Long stock
    ) {
        ProductItem productItem = mock(ProductItem.class);
        Product product = mock(Product.class);
        Seller seller = mock(Seller.class);

        when(productItem.getId()).thenReturn(itemId);
        when(productItem.isOnSale()).thenReturn(true);
        when(productItem.getStock()).thenReturn(stock);
        when(productItem.getProduct()).thenReturn(product);
        when(productItem.getPrice()).thenReturn(price);
        when(productItem.getOptionSummary()).thenReturn("옵션");
        when(product.isApproved()).thenReturn(true);
        when(product.getSeller()).thenReturn(seller);
        when(product.getName()).thenReturn("테스트 상품");
        when(product.getThumbnailUrl()).thenReturn("thumbnail.jpg");
        when(seller.isApproved()).thenReturn(true);

        return productItem;
    }

    private ProductItem productItemOnlyOnSale(
        Long itemId,
        boolean onSale
    ) {
        ProductItem productItem = mock(ProductItem.class);

        when(productItem.getId()).thenReturn(itemId);
        when(productItem.isOnSale()).thenReturn(onSale);

        return productItem;
    }

    private ProductItem productItemForStockValidation(
        Long itemId,
        boolean onSale,
        Long stock
    ) {
        ProductItem productItem = mock(ProductItem.class);

        when(productItem.getId()).thenReturn(itemId);
        when(productItem.isOnSale()).thenReturn(onSale);
        when(productItem.getStock()).thenReturn(stock);

        return productItem;
    }

    private CartItem cartItem(
        Long userId,
        ProductItem productItem,
        Integer quantity
    ) {
        CartItem cartItem = mock(CartItem.class);
        Cart cart = mock(Cart.class);
        User user = mock(User.class);

        when(cartItem.getCart()).thenReturn(cart);
        when(cart.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);
        when(cartItem.getProductItem()).thenReturn(productItem);
        when(cartItem.getQuantity()).thenReturn(quantity);

        return cartItem;
    }

    private Order cancelableOrder(
        Long orderId,
        Long userId,
        Long finalPrice,
        AtomicReference<OrderStatus> statusRef
    ) {
        Order order = orderForStatusAndIdValidation(
            orderId,
            userId,
            statusRef.get()
        );

        when(order.getFinalPrice()).thenReturn(finalPrice);

        when(order.getStatus())
            .thenAnswer(invocation -> statusRef.get());

        doAnswer(invocation -> {
            statusRef.set(OrderStatus.CANCELLED);
            return null;
        }).when(order).cancel();

        return order;
    }

    private OrderItem cancelableOrderItem(
        Long orderItemId,
        ProductItem productItem,
        Integer quantity
    ) {
        OrderItem orderItem = mock(OrderItem.class);

        when(orderItem.getId()).thenReturn(orderItemId);
        when(orderItem.getProductItem()).thenReturn(productItem);
        when(orderItem.getQuantity()).thenReturn(quantity);

        return orderItem;
    }

    private OrderItem orderItemOnlyId(Long orderItemId) {
        OrderItem orderItem = mock(OrderItem.class);

        when(orderItem.getId()).thenReturn(orderItemId);

        return orderItem;
    }

    private Order orderForOwnerValidation(Long userId) {
        Order order = mock(Order.class);
        User user = mock(User.class);

        when(order.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);

        return order;
    }

    private Order orderForStatusValidation(
        Long userId,
        OrderStatus status
    ) {
        Order order = orderForOwnerValidation(userId);

        when(order.getStatus()).thenReturn(status);

        return order;
    }

    private Order orderForStatusAndIdValidation(
        Long orderId,
        Long userId,
        OrderStatus status
    ) {
        Order order = orderForStatusValidation(
            userId,
            status
        );

        when(order.getId()).thenReturn(orderId);

        return order;
    }

    private CartItem cartItemOwnerOnly(Long userId) {
        CartItem cartItem = mock(CartItem.class);
        Cart cart = mock(Cart.class);
        User user = mock(User.class);

        when(cartItem.getCart()).thenReturn(cart);
        when(cart.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);

        return cartItem;
    }

    private void mockProductItemsWithLock(
        List<Long> itemIds,
        List<ProductItem> productItems
    ) {
        when(productItemRepository.findAllByIdInForUpdate(itemIds))
            .thenReturn(productItems);
    }

    private void mockOrderWithLock(
        Long orderId,
        Order order
    ) {
        when(orderRepository.findByIdWithLock(orderId))
            .thenReturn(Optional.of(order));
    }

    private void mockOrderNotFoundWithLock(Long orderId) {
        when(orderRepository.findByIdWithLock(orderId))
            .thenReturn(Optional.empty());
    }

    private ProductItem productItemForDiscountLimitValidation(
        Long itemId,
        Long price,
        Long stock
    ) {
        ProductItem productItem = mock(ProductItem.class);
        Product product = mock(Product.class);
        Seller seller = mock(Seller.class);

        when(productItem.getId()).thenReturn(itemId);
        when(productItem.isOnSale()).thenReturn(true);
        when(productItem.getStock()).thenReturn(stock);
        when(productItem.getProduct()).thenReturn(product);
        when(productItem.getPrice()).thenReturn(price);

        when(product.isApproved()).thenReturn(true);
        when(product.getSeller()).thenReturn(seller);
        when(seller.isApproved()).thenReturn(true);

        return productItem;
    }

}
