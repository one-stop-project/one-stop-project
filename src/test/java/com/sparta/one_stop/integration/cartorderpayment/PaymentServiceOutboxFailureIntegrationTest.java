package com.sparta.one_stop.integration.cartorderpayment;

import com.sparta.one_stop.domain.delivery.repository.DeliveryHistoryRepository;
import com.sparta.one_stop.domain.delivery.repository.DeliveryRepository;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderItemRequest;
import com.sparta.one_stop.domain.order.dto.request.CreateOrderRequest;
import com.sparta.one_stop.domain.order.dto.response.CreateOrderResponse;
import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.order.service.OrderCommandService;
import com.sparta.one_stop.domain.payment.dto.request.ApprovePaymentRequest;
import com.sparta.one_stop.domain.payment.dto.response.ApprovePaymentResponse;
import com.sparta.one_stop.domain.payment.entity.Payment;
import com.sparta.one_stop.domain.payment.repository.PaymentRepository;
import com.sparta.one_stop.domain.payment.service.PaymentService;
import com.sparta.one_stop.domain.point.dto.request.PointChargeRequest;
import com.sparta.one_stop.domain.point.entity.Point;
import com.sparta.one_stop.domain.point.entity.PointHistory;
import com.sparta.one_stop.domain.point.repository.PointHistoryRepository;
import com.sparta.one_stop.domain.point.repository.PointRepository;
import com.sparta.one_stop.domain.point.repository.PointUsageDetailRepository;
import com.sparta.one_stop.domain.point.service.PointService;
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
import com.sparta.one_stop.global.enums.point.PointHistoryType;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.outbox.entity.OutboxEvent;
import com.sparta.one_stop.global.outbox.repository.OutboxEventRepository;
import com.sparta.one_stop.integration.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * 결제 승인 Outbox 실패 격리 통합 테스트
 *
 * 목적:
 * - Outbox 저장 실패가 발생해도 결제 승인 트랜잭션이 rollback-only로 오염되지 않는지 검증한다.
 * - 단위 테스트의 @Mock 기반 검증으로는 실제 트랜잭션 경계를 확인할 수 없으므로
 *   @SpringBootTest 기반 통합 테스트로 검증한다.
 *
 * 주의:
 * - 테스트 메서드 트랜잭션 롤백과 서비스 트랜잭션 커밋 검증이 충돌하지 않도록
 *   IntegrationTestSupport의 기본 트랜잭션을 비활성화한다.
 * - 테스트 데이터는 @AfterEach에서 직접 정리한다.
 */
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentServiceOutboxFailureIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private OrderCommandService orderCommandService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PointService pointService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryHistoryRepository deliveryHistoryRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    @Autowired
    private PointUsageDetailRepository pointUsageDetailRepository;

    @MockitoSpyBean
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private RedissonClient redissonClient;

    private User buyer;
    private Seller seller;
    private ProductItem productItem;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();

        buyer = userRepository.save(User.builder()
            .email("buyer-" + suffix + "@test.com")
            .password("password1!")
            .name("구매자")
            .phone("010-1234-5678")
            .address("서울시 강남구")
            .role(UserRole.BUYER)
            .build()
        );

        User sellerUser = userRepository.save(User.builder()
            .email("seller-" + suffix + "@test.com")
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
        seller = sellerRepository.saveAndFlush(seller);

        Product product = productRepository.save(Product.builder()
            .seller(seller)
            .name("테스트 상품")
            .description("Outbox 실패 격리 통합 테스트용 상품입니다.")
            .thumbnailUrl("thumbnail.jpg")
            .optionName1("색상")
            .optionName2("")
            .optionName3("")
            .optionName4("")
            .optionName5("")
            .build()
        );
        product.approve();
        product = productRepository.saveAndFlush(product);

        productItem = productItemRepository.save(ProductItem.builder()
            .product(product)
            .optionValue1("블랙")
            .optionValue2("")
            .optionValue3("")
            .optionValue4("")
            .optionValue5("")
            .price(10_000L)
            .stock(100L)
            .build()
        );

        // 주문 생성 및 포인트 결제를 위해 사전 충전
        pointService.chargePoint(
            buyer.getId(),
            new PointChargeRequest(10_000)
        );
    }

    @AfterEach
    void tearDown() {
        entityManager.clear();

        pointUsageDetailRepository.deleteAllInBatch();
        pointHistoryRepository.deleteAllInBatch();
        pointRepository.deleteAllInBatch();

        outboxEventRepository.deleteAllInBatch();

        paymentRepository.deleteAllInBatch();

        deliveryHistoryRepository.deleteAllInBatch();
        deliveryRepository.deleteAllInBatch();

        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();

        productItemRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        sellerRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("Outbox 저장 실패가 발생해도 결제 승인 트랜잭션은 롤백되지 않는다")
    void approvePayment_success_whenOutboxSaveFails_doesNotRollbackPaymentTransaction() {
        // given
        Integer usedPoint = 5_000;

        CreateOrderResponse orderResponse = orderCommandService.createOrder(
            buyer.getId(),
            new CreateOrderRequest(
                OrderType.DIRECT,
                List.of(new CreateOrderItemRequest(
                    productItem.getId(),
                    2
                )),
                null,
                "홍길동",
                "010-1234-5678",
                "서울시 강남구",
                "문 앞에 놓아주세요",
                null,
                usedPoint
            )
        );

        Long orderId = orderResponse.orderId();
        Long paymentAmount = orderResponse.finalPrice();

        assertThat(paymentAmount).isEqualTo(18_000L);

        ApprovePaymentRequest paymentRequest = new ApprovePaymentRequest(
            orderId,
            paymentAmount
        );

        doThrow(new DataIntegrityViolationException("duplicate event_id"))
            .when(outboxEventRepository)
            .saveAndFlush(any(OutboxEvent.class));

        // when
        ApprovePaymentResponse response = paymentService.approvePayment(
            buyer.getId(),
            paymentRequest
        );

        // then 1: 결제 API는 성공 응답을 반환한다
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.finalPrice()).isEqualTo(paymentAmount);
        assertThat(response.status()).isEqualTo(OrderStatus.PAID);

        entityManager.clear();

        // then 2: 주문은 PAID로 커밋된다
        Order paidOrder = orderRepository.findById(orderId)
            .orElseThrow();

        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // then 3: Payment도 정상 저장된다
        List<Payment> payments = paymentRepository.findAll().stream()
            .filter(payment -> payment.getOrder()
                .getId()
                .equals(orderId))
            .toList();

        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.PAID);

        // then 4: 포인트 차감도 정상 커밋된다
        Point point = pointRepository.findByUserId(buyer.getId())
            .orElseThrow();

        assertThat(point.getBalance()).isEqualTo(5_000);

        List<PointHistory> useHistories = pointHistoryRepository.findAll().stream()
            .filter(history -> history.getType() == PointHistoryType.USE)
            .filter(history -> history.getAmount().equals(-usedPoint))
            .toList();

        assertThat(useHistories).hasSize(1);

        // then 5: Outbox 저장은 실패했으므로 이벤트는 저장되지 않는다
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll().stream()
            .filter(event -> event.getEventId().startsWith("payment-approved-"))
            .toList();

        assertThat(outboxEvents).isEmpty();
    }

}
