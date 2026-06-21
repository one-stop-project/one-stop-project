package com.sparta.one_stop.integration.delivery;

import com.sparta.one_stop.domain.delivery.dto.request.RejectOrderRequest;
import com.sparta.one_stop.domain.delivery.dto.request.ShipDeliveryRequest;
import com.sparta.one_stop.domain.delivery.dto.request.UpdateDeliveryStatusRequest;
import com.sparta.one_stop.domain.delivery.dto.response.ConfirmOrderResponse;
import com.sparta.one_stop.domain.delivery.dto.response.RejectOrderResponse;
import com.sparta.one_stop.domain.delivery.dto.response.ShipDeliveryResponse;
import com.sparta.one_stop.domain.delivery.dto.response.UpdateDeliveryStatusResponse;
import com.sparta.one_stop.domain.delivery.entity.Delivery;
import com.sparta.one_stop.domain.delivery.entity.DeliveryHistory;
import com.sparta.one_stop.domain.delivery.repository.DeliveryHistoryRepository;
import com.sparta.one_stop.domain.delivery.repository.DeliveryRepository;
import com.sparta.one_stop.domain.delivery.service.DeliveryService;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderItemRequest;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderRequest;
import com.sparta.one_stop.domain.order.dto.response.CreateOrderResponse;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.order.service.OrderCommandService;
import com.sparta.one_stop.domain.payment.dto.request.ApprovePaymentRequest;
import com.sparta.one_stop.domain.payment.service.PaymentService;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.delivery.DeliveryStatus;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.order.OrderType;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.integration.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 배송 도메인 통합 테스트
 *
 * 결제 완료 후 생성된 배송에 대해 발주 확인, 운송장 등록, 배송 상태 변경, 주문 거절 플로우를
 * 실제 DB(Testcontainers MySQL)에서 검증한다.
 */
@Tag("integration")
class DeliveryIntegrationTest extends IntegrationTestSupport {

    @Autowired private DeliveryService deliveryService;
    @Autowired private OrderCommandService orderCommandService;
    @Autowired private PaymentService paymentService;
    @Autowired private UserRepository userRepository;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductItemRepository productItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private DeliveryRepository deliveryRepository;
    @Autowired private DeliveryHistoryRepository deliveryHistoryRepository;

    @MockitoBean private RedissonClient redissonClient;

    private User buyer;
    private User sellerUser;
    private Seller seller;
    private ProductItem productItem;
    private ProductItem productItem2;

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(User.builder()
            .email("buyer@test.com")
            .password("password1!")
            .name("구매자")
            .phone("010-1234-5678")
            .address("서울시 강남구")
            .role(UserRole.BUYER)
            .build()
        );

        sellerUser = userRepository.save(User.builder()
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

        Product product = productRepository.save(Product.builder()
            .seller(seller)
            .name("테스트 상품")
            .description("배송 통합 테스트용 상품입니다.")
            .thumbnailUrl("thumbnail.jpg")
            .optionName1("색상")
            .optionName2("")
            .optionName3("")
            .optionName4("")
            .optionName5("")
            .build()
        );
        product.approve();

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

        productItem2 = productItemRepository.save(ProductItem.builder()
            .product(product)
            .optionValue1("화이트")
            .optionValue2("")
            .optionValue3("")
            .optionValue4("")
            .optionValue5("")
            .price(10000L)
            .stock(100L)
            .build()
        );
    }

    /**
     * DIRECT 주문 생성 → 결제 승인 → 배송 자동 생성까지 완료된 상태를 만든다.
     */
    private CreateOrderResponse createPaidOrder(List<CreateOrderItemRequest> items) {
        CreateOrderResponse orderResponse = orderCommandService.createOrder(
            buyer.getId(),
            new CreateOrderRequest(
                OrderType.DIRECT, items, null,
                "홍길동", "010-1234-5678", "서울시 강남구",
                "문 앞에 놓아주세요", null, 0
            )
        );

        paymentService.approvePayment(
            buyer.getId(),
            new ApprovePaymentRequest(orderResponse.orderId(), orderResponse.finalPrice())
        );

        return orderResponse;
    }

    private OrderItem findOrderItem(Long orderId) {
        return orderItemRepository.findAllByOrderId(orderId).get(0);
    }

    private Delivery findDelivery(Long orderId) {
        return deliveryRepository.findAllByOrderItem_Order_Id(orderId).get(0);
    }

    @Nested
    @DisplayName("배송 정상 플로우")
    class HappyPath {

        @Test
        @DisplayName("결제 완료 후 발주 확인 → 운송장 등록 → 배송중 → 배송 완료까지 전이된다")
        void fullFlow_confirm_ship_deliver() {
            // given: 결제 완료 (배송 자동 생성)
            CreateOrderResponse orderRes = createPaidOrder(
                List.of(new CreateOrderItemRequest(productItem.getId(), 2))
            );

            OrderItem oi = findOrderItem(orderRes.orderId());
            Delivery delivery = findDelivery(orderRes.orderId());

            assertThat(oi.getStatus()).isEqualTo(OrderItemStatus.ORDERED);
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.ACCEPT);

            // 1단계: 발주 확인 (ORDERED → CONFIRMED / ACCEPT → INSTRUCT)
            ConfirmOrderResponse confirmRes = deliveryService.confirmOrder(
                oi.getId(), sellerUser.getId()
            );

            assertThat(confirmRes.orderItemStatus()).isEqualTo(OrderItemStatus.CONFIRMED);
            assertThat(confirmRes.deliveryStatus()).isEqualTo(DeliveryStatus.INSTRUCT);

            // 2단계: 운송장 등록 (INSTRUCT → DEPARTURE / CONFIRMED → SHIPPING)
            ShipDeliveryResponse shipRes = deliveryService.shipDelivery(
                delivery.getId(), sellerUser.getId(),
                new ShipDeliveryRequest("CJ대한통운", "1234567890")
            );

            assertThat(shipRes.status()).isEqualTo(DeliveryStatus.DEPARTURE);
            assertThat(shipRes.deliveryCompany()).isEqualTo("CJ대한통운");

            // 3단계: 배송중 (DEPARTURE → DELIVERING)
            UpdateDeliveryStatusResponse deliveringRes = deliveryService.updateDeliveryStatus(
                delivery.getId(), sellerUser.getId(),
                new UpdateDeliveryStatusRequest(DeliveryStatus.DELIVERING)
            );

            assertThat(deliveringRes.status()).isEqualTo(DeliveryStatus.DELIVERING);

            // 4단계: 배송 완료 (DELIVERING → FINAL_DELIVERY / SHIPPING → DELIVERED)
            UpdateDeliveryStatusResponse finalRes = deliveryService.updateDeliveryStatus(
                delivery.getId(), sellerUser.getId(),
                new UpdateDeliveryStatusRequest(DeliveryStatus.FINAL_DELIVERY)
            );

            assertThat(finalRes.status()).isEqualTo(DeliveryStatus.FINAL_DELIVERY);

            // DB 검증: OrderItem DELIVERED
            OrderItem deliveredOi = orderItemRepository.findById(oi.getId()).orElseThrow();
            assertThat(deliveredOi.getStatus()).isEqualTo(OrderItemStatus.DELIVERED);

            // DB 검증: DeliveryHistory 5건 (ACCEPT, INSTRUCT, DEPARTURE, DELIVERING, FINAL_DELIVERY)
            List<DeliveryHistory> histories = deliveryHistoryRepository
                .findAllByDeliveryIdOrderByChangedAtAsc(delivery.getId());
            assertThat(histories).hasSize(5);
        }
    }

    @Nested
    @DisplayName("주문 거절")
    class Reject {

        @Test
        @DisplayName("주문 거절 시 재고가 복구되고 배송이 ORDER_CANCELLED로 전이된다")
        void reject_restoresStockAndCancelsDelivery() {
            CreateOrderResponse orderRes = createPaidOrder(
                List.of(
                    new CreateOrderItemRequest(productItem.getId(), 2),
                    new CreateOrderItemRequest(productItem2.getId(), 1)
                )
            );

            List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(orderRes.orderId());
            OrderItem targetItem = orderItems.stream()
                .filter(oi -> oi.getProductItem().getId().equals(productItem.getId()))
                .findFirst().orElseThrow();

            // 거절 전 재고 확인 (100 - 2 = 98)
            ProductItem beforeReject = productItemRepository.findById(productItem.getId()).orElseThrow();
            assertThat(beforeReject.getStock()).isEqualTo(98L);

            RejectOrderResponse rejectRes = deliveryService.rejectOrder(
                targetItem.getId(), sellerUser.getId(),
                new RejectOrderRequest("재고 소진")
            );

            assertThat(rejectRes.orderItemStatus()).isEqualTo(OrderItemStatus.REJECTED);
            assertThat(rejectRes.deliveryStatus()).isEqualTo(DeliveryStatus.ORDER_CANCELLED);
            assertThat(rejectRes.rejectedPrice()).isEqualTo(20000L); // 10000 * 2
            assertThat(rejectRes.orderAutoCancelled()).isFalse(); // 다른 아이템 남아있음

            // 재고 복구 확인 (98 + 2 = 100)
            ProductItem afterReject = productItemRepository.findById(productItem.getId()).orElseThrow();
            assertThat(afterReject.getStock()).isEqualTo(100L);

            // 주문은 여전히 PAID (일부 거절이므로)
            Order order = orderRepository.findById(orderRes.orderId()).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @Test
        @DisplayName("모든 아이템이 거절되면 주문이 자동 취소된다")
        void rejectAll_autoCancelsOrder() {
            CreateOrderResponse orderRes = createPaidOrder(
                List.of(new CreateOrderItemRequest(productItem.getId(), 1))
            );

            OrderItem oi = findOrderItem(orderRes.orderId());

            RejectOrderResponse rejectRes = deliveryService.rejectOrder(
                oi.getId(), sellerUser.getId(),
                new RejectOrderRequest("재고 소진")
            );

            assertThat(rejectRes.orderAutoCancelled()).isTrue();

            // 주문 CANCELLED 확인
            Order order = orderRepository.findById(orderRes.orderId()).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("권한·상태 검증")
    class Validation {

        @Test
        @DisplayName("다른 판매자가 발주 확인하면 SELLER_007")
        void confirm_fail_differentSeller() {
            CreateOrderResponse orderRes = createPaidOrder(
                List.of(new CreateOrderItemRequest(productItem.getId(), 1))
            );
            OrderItem oi = findOrderItem(orderRes.orderId());

            // 다른 판매자 생성
            User otherSellerUser = userRepository.save(User.builder()
                .email("other-seller@test.com")
                .password("password1!")
                .name("다른판매자")
                .phone("010-0000-0000")
                .address("부산시")
                .role(UserRole.SELLER)
                .build()
            );
            Seller otherSeller = sellerRepository.save(Seller.builder()
                .user(otherSellerUser)
                .shopName("다른샵")
                .businessNumber("9999999999")
                .bankAccount("220-999-999999")
                .build()
            );
            otherSeller.approve();

            assertThatThrownBy(() ->
                deliveryService.confirmOrder(oi.getId(), otherSellerUser.getId())
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SELLER_007));
        }

        @Test
        @DisplayName("이미 CONFIRMED인 주문에 발주 확인하면 SELLER_008")
        void confirm_fail_alreadyConfirmed() {
            CreateOrderResponse orderRes = createPaidOrder(
                List.of(new CreateOrderItemRequest(productItem.getId(), 1))
            );
            OrderItem oi = findOrderItem(orderRes.orderId());

            // 첫 번째 확인 성공
            deliveryService.confirmOrder(oi.getId(), sellerUser.getId());

            // 두 번째 확인 실패
            assertThatThrownBy(() ->
                deliveryService.confirmOrder(oi.getId(), sellerUser.getId())
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SELLER_008));
        }

        @Test
        @DisplayName("ACCEPT 상태에서 운송장 등록하면 SHIPPING_001")
        void ship_fail_notInstruct() {
            CreateOrderResponse orderRes = createPaidOrder(
                List.of(new CreateOrderItemRequest(productItem.getId(), 1))
            );
            Delivery delivery = findDelivery(orderRes.orderId());

            // confirm 안 했으므로 ACCEPT 상태
            assertThatThrownBy(() ->
                deliveryService.shipDelivery(
                    delivery.getId(), sellerUser.getId(),
                    new ShipDeliveryRequest("CJ대한통운", "123456")
                )
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SHIPPING_001));
        }
    }
}
