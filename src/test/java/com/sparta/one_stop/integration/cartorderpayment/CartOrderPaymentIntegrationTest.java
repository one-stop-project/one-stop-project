package com.sparta.one_stop.integration.cartorderpayment;

import com.sparta.one_stop.domain.cart.dto.request.AddCartItemRequest;
import com.sparta.one_stop.domain.cart.entity.CartItem;
import com.sparta.one_stop.domain.cart.repository.CartItemRepository;
import com.sparta.one_stop.domain.cart.service.CartMergeService;
import com.sparta.one_stop.domain.cart.service.CartService;
import com.sparta.one_stop.domain.cart.support.GuestCartRedisKeyProvider;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderRequest;
import com.sparta.one_stop.domain.order.dto.response.CreateOrderResponse;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.order.service.OrderCommandService;
import com.sparta.one_stop.domain.payment.dto.request.ApprovePaymentRequest;
import com.sparta.one_stop.domain.payment.dto.response.ApprovePaymentResponse;
import com.sparta.one_stop.domain.payment.entity.Payment;
import com.sparta.one_stop.domain.payment.repository.PaymentRepository;
import com.sparta.one_stop.domain.payment.service.PaymentService;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.order.OrderType;
import com.sparta.one_stop.global.enums.payment.PaymentStatus;
import com.sparta.one_stop.global.enums.product.ProductItemStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.integration.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cart → Order → Payment 핵심 구매 플로우 통합 테스트
 *
 * 1차 커밋: 로그인 사용자 구매 성공 플로우
 * - 로그인 사용자가 상품을 장바구니에 담는다.
 * - 장바구니 상품을 기반으로 주문을 생성한다.
 * - 주문 생성 후 장바구니 상품이 삭제되는지 확인한다.
 * - 주문 생성 시 상품 재고가 차감되는지 확인한다.
 * - 결제 승인 후 주문/결제 상태가 정상적으로 변경되는지 확인한다.
 *
 * 2차 커밋: 대표 실패 플로우
 * - 주문 생성 시점에 재고가 부족하면 주문 생성에 실패하는지 확인한다.
 * - 이미 결제 완료된 주문에 대해 중복 결제 승인을 방지하는지 확인한다.
 *
 * 3차 커밋: 비로그인 장바구니 merge 플로우
 * - 비로그인 사용자가 Redis 장바구니에 상품을 담는다.
 * - Redis Hash에 수량이 저장되는지 확인한다.
 * - Redis ZSet에 담기 순서가 저장되는지 확인한다.
 * - CartMergeService의 Redisson Lock은 mock으로 제어하고, Redis Hash/ZSet은 실제 Redis Testcontainer로 검증한다.
 * - 로그인 시 Redis 장바구니가 DB 장바구니로 merge되는지 확인한다.
 * - merge 후 Redis Hash/ZSet key가 삭제되는지 확인한다.
 * - merge된 DB 장바구니 기준으로 주문 생성 및 결제 승인까지 완료되는지 확인한다.
 *
 * 4차 커밋: 추가 실패 플로우
 * - 장바구니에 담은 상품이 주문 생성 전에 판매 중지되면 주문 생성에 실패하는지 확인한다.
 * - 결제 승인 금액이 주문 최종 금액과 다르면 결제 승인에 실패하고 주문 상태가 유지되는지 확인한다.
 */
class CartOrderPaymentIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private CartService cartService;
    @Autowired
    private OrderCommandService orderCommandService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SellerRepository sellerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductItemRepository productItemRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private CartMergeService cartMergeService;
    @Autowired
    private GuestCartRedisKeyProvider guestCartRedisKeyProvider;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    private RedissonClient redissonClient;

    private User buyer;
    private Seller seller;
    private ProductItem productItem;

    @BeforeEach
    void setUp() {
        // 1. 구매자 생성
        buyer = userRepository.save(User.builder()
            .email("buyer@test.com")
            .password("password1!")
            .name("구매자")
            .phone("010-1234-5678")
            .address("서울시 강남구")
            .role(UserRole.BUYER)
            .build()
        );

        // 2. 판매자 생성 (User + Seller, APPROVED)
        User sellerUser = userRepository.save(User.builder()
            .email("seller@test.com")
            .password("password1!")
            .name("판매자")
            .phone("010-9876-5432")
            .address("서울시 서초구")
            .role(UserRole.SELLER)
            .build()
        );

        seller = sellerRepository.save(Seller.builder()
            .user(sellerUser)
            .shopName("테스트샵")
            .businessNumber("1234567890")
            .bankAccount("110-123-456789")
            .build()
        );
        seller.approve();

        // 3. 상품 생성 (APPROVED)
        Product product = productRepository.save(Product.builder()
            .seller(seller)
            .name("테스트 상품")
            .description("통합 테스트용 상품입니다.")
            .thumbnailUrl("thumbnail.jpg")
            .optionName1("색상")
            .optionName2("")
            .optionName3("")
            .optionName4("")
            .optionName5("")
            .build()
        );
        product.approve();

        // 4. 상품 옵션 생성 (ON_SALE, 재고 100개, 가격 10,000원)
        productItem = productItemRepository.save(ProductItem.builder()
            .product(product)
            .optionValue1("블랙")
            .optionValue2("")
            .optionValue3("")
            .optionValue4("")
            .optionValue5("")
            .price(10000L)
            .stock(100L)
            .build()
        );
    }

    @Test
    @DisplayName("로그인 사용자는 장바구니 상품으로 주문을 생성하고 결제 승인까지 완료할 수 있다")
    void loginUser_canCreateOrderFromCartAndApprovePayment() {
        // given: 장바구니에 상품 2개 담기
        cartService.addCartItem(
            buyer.getId(),
            new AddCartItemRequest(productItem.getId(), 2)
        );

        List<CartItem> cartItems = cartItemRepository.findAll().stream()
            .filter(ci -> ci.getCart().getUser().getId().equals(buyer.getId()))
            .toList();

        assertThat(cartItems).hasSize(1);
        assertThat(cartItems.get(0).getQuantity()).isEqualTo(2);

        Long cartItemId = cartItems.get(0).getId();

        // when 1: CART 주문 생성
        CreateOrderRequest orderRequest = new CreateOrderRequest(
            OrderType.CART,
            null,
            List.of(cartItemId),
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞에 놓아주세요",
            null,
            0
        );

        CreateOrderResponse orderResponse = orderCommandService.createOrder(
            buyer.getId(),
            orderRequest
        );

        // then 1: 주문 생성 검증
        assertThat(orderResponse).isNotNull();
        assertThat(orderResponse.finalPrice()).isEqualTo(23000L);

        Order order = orderRepository.findById(orderResponse.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getTotalPrice()).isEqualTo(20000L);
        assertThat(order.getDeliveryFee()).isEqualTo(3000L);

        // 장바구니 상품 삭제 확인
        assertThat(cartItemRepository.findById(cartItemId)).isEmpty();

        // 재고 차감 확인 (100 → 98)
        ProductItem updatedItem = productItemRepository.findById(productItem.getId()).orElseThrow();
        assertThat(updatedItem.getStock()).isEqualTo(98L);

        // when 2: 결제 승인
        ApprovePaymentResponse paymentResponse = paymentService.approvePayment(
            buyer.getId(),
            new ApprovePaymentRequest(orderResponse.orderId(), orderResponse.finalPrice())
        );

        // then 2: 결제 승인 검증
        assertThat(paymentResponse).isNotNull();

        Order paidOrder = orderRepository.findById(orderResponse.orderId()).orElseThrow();
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        Payment payment = paymentRepository.findByOrderId(orderResponse.orderId()).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("장바구니 상품 주문 생성 시 재고가 부족하면 주문 생성에 실패한다")
    void createOrderFromCart_fail_whenStockIsInsufficient() {
        // given: 구매자가 재고 100개인 상품 옵션 2개를 장바구니에 담는다.
        cartService.addCartItem(
            buyer.getId(),
            new AddCartItemRequest(productItem.getId(), 2)
        );

        List<CartItem> cartItems = cartItemRepository.findAll().stream()
            .filter(ci -> ci.getCart().getUser().getId().equals(buyer.getId()))
            .toList();

        assertThat(cartItems).hasSize(1);
        assertThat(cartItems.get(0).getQuantity()).isEqualTo(2);

        Long cartItemId = cartItems.get(0).getId();

        // given: 장바구니에 담은 이후, 주문 생성 전에 상품 재고가 1개로 감소한 상황을 만든다.
        // 이 테스트는 장바구니 담기 실패가 아니라 주문 생성 시점의 재고 부족을 검증한다.
        productItem.updateForAdjustment(
            null,
            null,
            1L
        );

        productItemRepository.saveAndFlush(productItem);

        CreateOrderRequest orderRequest = new CreateOrderRequest(
            OrderType.CART,
            null,
            List.of(cartItemId),
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞에 놓아주세요",
            null,
            0
        );

        // when & then: 주문 수량 2개가 현재 재고 1개를 초과하므로 주문 생성에 실패한다.
        assertThatThrownBy(() -> orderCommandService.createOrder(
            buyer.getId(),
            orderRequest
        ))
            .isInstanceOf(CustomException.class)
            .hasMessage("재고가 부족합니다");

        // then: 주문 생성 실패 시 장바구니 상품은 삭제되지 않는다.
        assertThat(cartItemRepository.findById(cartItemId)).isPresent();

        // then: 실패한 주문 생성으로 인해 재고가 추가 차감되지 않는다.
        ProductItem updatedItem = productItemRepository.findById(productItem.getId())
            .orElseThrow();

        assertThat(updatedItem.getStock()).isEqualTo(1L);
    }

    @Test
    @DisplayName("이미 결제 완료된 주문은 중복 결제 승인할 수 없다")
    void approvePayment_fail_whenOrderAlreadyPaid() {
        // given: 구매자가 상품 옵션 2개를 장바구니에 담는다.
        cartService.addCartItem(
            buyer.getId(),
            new AddCartItemRequest(productItem.getId(), 2)
        );

        List<CartItem> cartItems = cartItemRepository.findAll().stream()
            .filter(ci -> ci.getCart().getUser().getId().equals(buyer.getId()))
            .toList();

        assertThat(cartItems).hasSize(1);

        Long cartItemId = cartItems.get(0).getId();

        CreateOrderRequest orderRequest = new CreateOrderRequest(
            OrderType.CART,
            null,
            List.of(cartItemId),
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞에 놓아주세요",
            null,
            0
        );

        CreateOrderResponse orderResponse = orderCommandService.createOrder(
            buyer.getId(),
            orderRequest
        );

        ApprovePaymentRequest paymentRequest = new ApprovePaymentRequest(
            orderResponse.orderId(),
            orderResponse.finalPrice()
        );

        // given: 첫 번째 결제 승인은 정상 처리된다.
        ApprovePaymentResponse firstPaymentResponse = paymentService.approvePayment(
            buyer.getId(),
            paymentRequest
        );

        assertThat(firstPaymentResponse).isNotNull();

        Order paidOrder = orderRepository.findById(orderResponse.orderId())
            .orElseThrow();

        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        Payment paidPayment = paymentRepository.findByOrderId(orderResponse.orderId())
            .orElseThrow();

        assertThat(paidPayment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paidPayment.getApprovedAt()).isNotNull();

        // when & then: 이미 PAID 상태인 주문에 대해 다시 결제를 승인하면 실패한다.
        assertThatThrownBy(() -> paymentService.approvePayment(
            buyer.getId(),
            paymentRequest
        ))
            .isInstanceOf(CustomException.class);

        // then: 중복 결제 시도 이후에도 주문과 결제 상태는 PAID로 유지된다.
        Order afterDuplicatePaymentOrder = orderRepository.findById(orderResponse.orderId())
            .orElseThrow();

        Payment afterDuplicatePayment = paymentRepository.findByOrderId(orderResponse.orderId())
            .orElseThrow();

        assertThat(afterDuplicatePaymentOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(afterDuplicatePayment.getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("비로그인 장바구니는 로그인 시 DB 장바구니로 병합되고 주문/결제까지 완료할 수 있다")
    void guestCart_isMergedToUserCart_whenLoginAndCanCreateOrderAndPayment() throws InterruptedException {
        // given: 비로그인 장바구니를 식별할 guestCartId를 준비한다.
        // Redis 데이터는 트랜잭션 롤백 대상이 아니므로 테스트마다 고유한 key를 사용한다.
        String guestCartId = "guest-cart-" + UUID.randomUUID();

        String guestCartKey = guestCartRedisKeyProvider.buildGuestCartKey(guestCartId);
        String guestCartOrderKey = guestCartRedisKeyProvider.buildGuestCartOrderKey(guestCartId);

        redisTemplate.delete(guestCartKey);
        redisTemplate.delete(guestCartOrderKey);

        MockHttpServletResponse response = new MockHttpServletResponse();

        // given: 비로그인 사용자가 상품 옵션 2개를 장바구니에 담는다.
        cartService.addCartItem(
            null,
            guestCartId,
            response,
            new AddCartItemRequest(productItem.getId(), 2)
        );

        String itemField = String.valueOf(productItem.getId());

        // then: Redis Hash에 itemId → quantity 형태로 수량이 저장된다.
        Object savedQuantity = redisTemplate.opsForHash()
            .get(
                guestCartKey,
                itemField
            );

        assertThat(savedQuantity).isEqualTo("2");

        // then: Redis ZSet에 itemId가 저장되어 담기 순서 관리 대상이 된다.
        Set<String> orderedItemIds = redisTemplate.opsForZSet()
            .range(
                guestCartOrderKey,
                0,
                -1
            );

        assertThat(orderedItemIds)
            .isNotNull()
            .containsExactly(itemField);

        // given: CartMergeService가 사용하는 Redisson Lock은 테스트에서 mock으로 제어한다.
        // Redis Hash/ZSet은 실제 Redis Testcontainer를 사용하고, Lock 획득만 성공하도록 구성한다.
        RLock lock = mock(RLock.class);

        when(redissonClient.getLock("lock:cart:merge:" + guestCartId))
            .thenReturn(lock);

        when(lock.tryLock(
            anyLong(),
            anyLong(),
            eq(TimeUnit.SECONDS)
        )).thenReturn(true);

        when(lock.isHeldByCurrentThread())
            .thenReturn(true);

        // when: 사용자가 로그인하면 Redis 장바구니를 DB 장바구니로 병합한다.
        boolean merged = cartMergeService.mergeGuestCartToUserCart(
            buyer.getId(),
            guestCartId
        );

        // then: merge가 성공한다.
        assertThat(merged).isTrue();

        // then: merge 후 Redis Hash/ZSet key가 삭제된다.
        assertThat(redisTemplate.hasKey(guestCartKey)).isFalse();
        assertThat(redisTemplate.hasKey(guestCartOrderKey)).isFalse();

        // then: DB 장바구니에 Redis 장바구니 상품이 병합된다.
        List<CartItem> mergedCartItems = cartItemRepository.findAll().stream()
            .filter(cartItem -> cartItem.getCart()
                .getUser()
                .getId()
                .equals(buyer.getId()))
            .toList();

        assertThat(mergedCartItems).hasSize(1);
        assertThat(mergedCartItems.get(0).getProductItem().getId())
            .isEqualTo(productItem.getId());
        assertThat(mergedCartItems.get(0).getQuantity()).isEqualTo(2);

        Long cartItemId = mergedCartItems.get(0).getId();

        // when: merge된 DB 장바구니 상품을 기반으로 주문을 생성한다.
        CreateOrderRequest orderRequest = new CreateOrderRequest(
            OrderType.CART,
            null,
            List.of(cartItemId),
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞에 놓아주세요",
            null,
            0
        );

        CreateOrderResponse orderResponse = orderCommandService.createOrder(
            buyer.getId(),
            orderRequest
        );

        // then: 주문이 결제 대기 상태로 생성된다.
        assertThat(orderResponse).isNotNull();
        assertThat(orderResponse.finalPrice()).isEqualTo(23000L);

        Order order = orderRepository.findById(orderResponse.orderId())
            .orElseThrow();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getTotalPrice()).isEqualTo(20000L);
        assertThat(order.getDeliveryFee()).isEqualTo(3000L);

        // then: 주문 생성 후 DB 장바구니 상품은 삭제된다.
        assertThat(cartItemRepository.findById(cartItemId)).isEmpty();

        // then: 주문 수량만큼 상품 재고가 차감된다. 100개 - 2개 = 98개
        ProductItem updatedItem = productItemRepository.findById(productItem.getId())
            .orElseThrow();

        assertThat(updatedItem.getStock()).isEqualTo(98L);

        // when: 생성된 주문 금액으로 결제를 승인한다.
        ApprovePaymentResponse paymentResponse = paymentService.approvePayment(
            buyer.getId(),
            new ApprovePaymentRequest(
                orderResponse.orderId(),
                orderResponse.finalPrice()
            )
        );

        // then: 결제 승인 후 주문 상태는 PAID가 된다.
        assertThat(paymentResponse).isNotNull();

        Order paidOrder = orderRepository.findById(orderResponse.orderId())
            .orElseThrow();

        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // then: 결제 정보도 PAID 상태로 저장되고 승인 시간이 기록된다.
        Payment payment = paymentRepository.findByOrderId(orderResponse.orderId())
            .orElseThrow();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("장바구니 상품이 주문 생성 전에 판매 중지되면 주문 생성에 실패한다")
    void createOrderFromCart_fail_whenProductItemIsStoppedBeforeOrder() {
        // given: 구매자가 판매 중인 상품 옵션 2개를 장바구니에 담는다.
        cartService.addCartItem(
            buyer.getId(),
            new AddCartItemRequest(productItem.getId(), 2)
        );

        List<CartItem> cartItems = cartItemRepository.findAll().stream()
            .filter(ci -> ci.getCart().getUser().getId().equals(buyer.getId()))
            .toList();

        assertThat(cartItems).hasSize(1);
        assertThat(cartItems.get(0).getQuantity()).isEqualTo(2);

        Long cartItemId = cartItems.get(0).getId();

        // given: 장바구니에 담은 이후, 주문 생성 전에 상품 옵션이 판매 중지된 상황을 만든다.
        // 이 테스트는 장바구니 담기 실패가 아니라 주문 생성 시점의 판매 상태 검증을 확인한다.
        productItem.updateForAdjustment(
            null,
            ProductItemStatus.STOP,
            null
        );

        productItemRepository.saveAndFlush(productItem);

        CreateOrderRequest orderRequest = new CreateOrderRequest(
            OrderType.CART,
            null,
            List.of(cartItemId),
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞에 놓아주세요",
            null,
            0
        );

        // when & then: 판매 중지 상품은 주문 생성 대상이 될 수 없다.
        assertThatThrownBy(() -> orderCommandService.createOrder(
            buyer.getId(),
            orderRequest
        ))
            .isInstanceOf(CustomException.class);

        // then: 주문 생성 실패 시 장바구니 상품은 삭제되지 않는다.
        assertThat(cartItemRepository.findById(cartItemId)).isPresent();

        // then: 실패한 주문 생성으로 인해 재고가 차감되지 않는다.
        ProductItem stoppedItem = productItemRepository.findById(productItem.getId())
            .orElseThrow();

        assertThat(stoppedItem.isOnSale()).isFalse();
        assertThat(stoppedItem.getStock()).isEqualTo(100L);
    }

    @Test
    @DisplayName("결제 승인 금액이 주문 금액과 다르면 결제에 실패하고 주문 상태는 결제 대기로 유지된다")
    void approvePayment_fail_whenAmountMismatchAndOrderStatusRemainsPendingPayment() {
        // given: 구매자가 상품 옵션 2개를 장바구니에 담는다.
        cartService.addCartItem(
            buyer.getId(),
            new AddCartItemRequest(productItem.getId(), 2)
        );

        List<CartItem> cartItems = cartItemRepository.findAll().stream()
            .filter(ci -> ci.getCart().getUser().getId().equals(buyer.getId()))
            .toList();

        assertThat(cartItems).hasSize(1);

        Long cartItemId = cartItems.get(0).getId();

        CreateOrderRequest orderRequest = new CreateOrderRequest(
            OrderType.CART,
            null,
            List.of(cartItemId),
            "홍길동",
            "010-1234-5678",
            "서울시 강남구",
            "문 앞에 놓아주세요",
            null,
            0
        );

        CreateOrderResponse orderResponse = orderCommandService.createOrder(
            buyer.getId(),
            orderRequest
        );

        Order pendingOrder = orderRepository.findById(orderResponse.orderId())
            .orElseThrow();

        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(orderResponse.finalPrice()).isEqualTo(23000L);

        // when & then: 주문 최종 금액과 다른 금액으로 결제를 승인하면 실패한다.
        assertThatThrownBy(() -> paymentService.approvePayment(
            buyer.getId(),
            new ApprovePaymentRequest(
                orderResponse.orderId(),
                orderResponse.finalPrice() - 1000L
            )
        ))
            .isInstanceOf(CustomException.class);

        // then: 결제 실패 후에도 주문 상태는 결제 대기 상태로 유지된다.
        Order afterFailedPaymentOrder = orderRepository.findById(orderResponse.orderId())
            .orElseThrow();

        assertThat(afterFailedPaymentOrder.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        // then: 결제 실패 시 Payment는 생성되지 않는다.
        assertThat(paymentRepository.findByOrderId(orderResponse.orderId())).isEmpty();

        // then: 주문 생성 시점에 차감된 재고는 결제 실패만으로 자동 복구되지 않는다.
        ProductItem updatedItem = productItemRepository.findById(productItem.getId())
            .orElseThrow();

        assertThat(updatedItem.getStock()).isEqualTo(98L);
    }

}
