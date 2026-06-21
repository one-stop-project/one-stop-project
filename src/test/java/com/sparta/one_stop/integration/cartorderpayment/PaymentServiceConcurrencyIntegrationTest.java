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
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동일 주문 동시 결제 승인 직렬화 통합 테스트
 *
 * 목적:
 * - 동일 orderId에 대한 결제 승인 요청이 동시에 들어와도 Order 비관적 락으로 직렬화되는지 검증한다.
 * - 실제 DB 락과 멀티스레드 동작을 확인해야 하므로 @SpringBootTest 기반 통합 테스트로 수행한다.
 *
 * 주의:
 * - 멀티스레드 테스트에서는 테스트 메서드 트랜잭션 롤백과 서비스 트랜잭션이 충돌할 수 있다.
 * - 따라서 IntegrationTestSupport의 기본 @Transactional을 비활성화하고, 테스트 데이터는 @AfterEach에서 수동 정리한다.
 */
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentServiceConcurrencyIntegrationTest extends IntegrationTestSupport {

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

    @Autowired
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

        seller = Seller.builder()
            .user(sellerUser)
            .shopName("테스트샵")
            .businessNumber("1234567890")
            .bankAccount("110-123-456789")
            .build();

        seller.approve();

        seller = sellerRepository.saveAndFlush(seller);

        Product product = Product.builder()
            .seller(seller)
            .name("테스트 상품")
            .description("동시 결제 승인 통합 테스트용 상품입니다.")
            .thumbnailUrl("thumbnail.jpg")
            .optionName1("색상")
            .optionName2("")
            .optionName3("")
            .optionName4("")
            .optionName5("")
            .build();

        product.approve();

        product = productRepository.saveAndFlush(product);

        productItem = productItemRepository.saveAndFlush(ProductItem.builder()
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

        // 포인트 사용 결제를 위해 사전 충전
        pointService.chargePoint(
            buyer.getId(),
            new PointChargeRequest(10_000)
        );
    }

    @AfterEach
    void tearDown() {
        entityManager.clear();

        // FK 역순 정리
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
    @DisplayName("동일 orderId 동시 결제 승인 요청은 비관적 락으로 직렬화되어 1건만 성공한다")
    void approvePayment_concurrently_sameOrderId_onlyOneSuccess() throws Exception {
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

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<PaymentAttemptResult> task = () -> {
            readyLatch.countDown();
            startLatch.await();

            try {
                ApprovePaymentResponse response = paymentService.approvePayment(
                    buyer.getId(),
                    paymentRequest
                );

                return PaymentAttemptResult.success(response);
            } catch (Throwable e) {
                return PaymentAttemptResult.failure(e);
            }
        };

        Future<PaymentAttemptResult> future1 = executorService.submit(task);
        Future<PaymentAttemptResult> future2 = executorService.submit(task);

        readyLatch.await(3, TimeUnit.SECONDS);
        startLatch.countDown();

        PaymentAttemptResult result1 = future1.get(10, TimeUnit.SECONDS);
        PaymentAttemptResult result2 = future2.get(10, TimeUnit.SECONDS);

        executorService.shutdown();
        executorService.awaitTermination(3, TimeUnit.SECONDS);

        List<PaymentAttemptResult> results = List.of(
            result1,
            result2
        );

        // then 1: 동시 요청 중 1건만 성공하고 1건은 실패한다
        assertThat(results)
            .filteredOn(PaymentAttemptResult::success)
            .hasSize(1);

        assertThat(results)
            .filteredOn(result -> !result.success())
            .hasSize(1);

        PaymentAttemptResult failedResult = results.stream()
            .filter(result -> !result.success())
            .findFirst()
            .orElseThrow();

        assertThat(failedResult.exception())
            .isInstanceOf(CustomException.class);

        CustomException failedException =
            (CustomException) failedResult.exception();

        assertThat(failedException.getErrorCode())
            .isIn(
                ErrorCode.PAYMENT_001,
                ErrorCode.PAYMENT_003
            );

        entityManager.clear();

        // then 2: 주문은 PAID 상태가 된다
        Order paidOrder = orderRepository.findById(orderId)
            .orElseThrow();

        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // then 3: Payment는 1건만 생성된다
        List<Payment> payments = paymentRepository.findAll().stream()
            .filter(payment -> payment.getOrder()
                .getId()
                .equals(orderId))
            .toList();

        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.PAID);

        // then 4: 포인트는 1회만 차감된다
        Point point = pointRepository.findByUserId(buyer.getId())
            .orElseThrow();

        assertThat(point.getBalance()).isEqualTo(5_000);

        List<PointHistory> useHistories = pointHistoryRepository.findAll().stream()
            .filter(history -> history.getType() == PointHistoryType.USE)
            .filter(history -> history.getAmount().equals(-usedPoint))
            .toList();

        assertThat(useHistories).hasSize(1);
    }

    private record PaymentAttemptResult(
        boolean success,
        ApprovePaymentResponse response,
        Throwable exception
    ) {

        static PaymentAttemptResult success(ApprovePaymentResponse response) {
            return new PaymentAttemptResult(
                true,
                response,
                null
            );
        }

        static PaymentAttemptResult failure(Throwable exception) {
            return new PaymentAttemptResult(
                false,
                null,
                exception
            );
        }
    }

}
