package com.sparta.one_stop.integration.cartorderpayment;

import com.sparta.one_stop.domain.cart.dto.request.AddCartItemRequest;
import com.sparta.one_stop.domain.cart.entity.CartItem;
import com.sparta.one_stop.domain.cart.repository.CartItemRepository;
import com.sparta.one_stop.domain.cart.service.CartService;
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
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.integration.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

}
