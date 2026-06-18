package com.sparta.one_stop.integration.review;

import com.sparta.one_stop.domain.order.entity.Order;
import com.sparta.one_stop.domain.order.entity.OrderItem;
import com.sparta.one_stop.domain.order.repository.OrderItemRepository;
import com.sparta.one_stop.domain.order.repository.OrderRepository;
import com.sparta.one_stop.domain.product.entity.Product;
import com.sparta.one_stop.domain.product.entity.ProductItem;
import com.sparta.one_stop.domain.product.repository.ProductItemRepository;
import com.sparta.one_stop.domain.product.repository.ProductRepository;
import com.sparta.one_stop.domain.review.dto.request.CreateReviewRequest;
import com.sparta.one_stop.domain.review.dto.response.ReviewResponse;
import com.sparta.one_stop.domain.review.entity.Review;
import com.sparta.one_stop.domain.review.repository.ReviewRepository;
import com.sparta.one_stop.domain.review.service.ReviewService;
import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.SellerRepository;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import com.sparta.one_stop.global.enums.order.OrderItemStatus;
import com.sparta.one_stop.global.enums.order.OrderType;
import com.sparta.one_stop.global.enums.review.ReviewStatus;
import com.sparta.one_stop.global.enums.user.UserRole;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import com.sparta.one_stop.global.security.AuthUser;
import com.sparta.one_stop.integration.IntegrationTestSupport;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 리뷰 통합 테스트
 *
 * 실제 DB(Testcontainers MySQL)를 사용하여 리뷰 생성·수정·삭제 플로우를 검증한다.
 *
 * 1. 서비스 레벨: 주문 상태별 리뷰 작성 가능 여부, 중복 리뷰 차단, 권한 검증
 * 2. Bean Validation: 별점 경계값(0, 6), 내용 길이 제한 (@Min/@Max/@Size)
 */
class ReviewIntegrationTest extends IntegrationTestSupport {

    @Autowired private ReviewService reviewService;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SellerRepository sellerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductItemRepository productItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private Validator validator;

    private User buyer;
    private User anotherBuyer;
    private Seller seller;
    private Product product;
    private ProductItem productItem;

    @BeforeEach
    void setUp() {
        // 구매자
        buyer = userRepository.save(User.builder()
            .email("buyer@test.com")
            .password("password1!")
            .name("구매자")
            .phone("010-1234-5678")
            .address("서울시 강남구")
            .role(UserRole.BUYER)
            .build()
        );

        // 다른 구매자 (타인 검증용)
        anotherBuyer = userRepository.save(User.builder()
            .email("another@test.com")
            .password("password1!")
            .name("다른구매자")
            .phone("010-9999-9999")
            .address("서울시 서초구")
            .role(UserRole.BUYER)
            .build()
        );

        // 판매자
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

        // 상품
        product = productRepository.save(Product.builder()
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

        // 상품 옵션
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

    /**
     * 주문 + 주문상품을 생성하고 지정한 상태까지 전이시킨다.
     */
    private OrderItem createOrderItemWithStatus(User orderUser, OrderItemStatus targetStatus) {
        Order order = orderRepository.saveAndFlush(new Order(
            orderUser, null,
            10000L, 0L, 13000L, 0, 0L,
            "홍길동", "010-1234-5678", "서울시 강남구",
            "문 앞에 놓아주세요", 3000L, OrderType.DIRECT
        ));
        order.completePayment();

        OrderItem orderItem = orderItemRepository.saveAndFlush(new OrderItem(
            order, productItem, seller,
            "테스트 상품 (블랙)", 1, 10000L, "thumbnail.jpg"
        ));

        // 상태 전이: PENDING_PAYMENT → ORDERED → CONFIRMED → SHIPPING → DELIVERED
        orderItem.markOrdered();

        if (targetStatus == OrderItemStatus.ORDERED) return orderItem;

        orderItem.confirm();
        if (targetStatus == OrderItemStatus.CONFIRMED) return orderItem;

        orderItem.startShipping();
        if (targetStatus == OrderItemStatus.SHIPPING) return orderItem;

        orderItem.completeDelivery();
        return orderItem;
    }

    private AuthUser authOf(User user) {
        return new AuthUser(user.getId(), user.getRole());
    }

    @Nested
    @DisplayName("리뷰 생성")
    class CreateReview {

        @Test
        @DisplayName("배송 완료 주문에 리뷰를 작성할 수 있다")
        void success() {
            OrderItem oi = createOrderItemWithStatus(buyer, OrderItemStatus.DELIVERED);

            CreateReviewRequest req = new CreateReviewRequest();
            setField(req, "orderItemId", oi.getId());
            setField(req, "rating", 5);
            setField(req, "content", "정말 좋은 상품입니다 추천합니다");

            ReviewResponse response = reviewService.createReview(authOf(buyer), req, null);

            assertThat(response.reviewId()).isNotNull();
            assertThat(response.rating()).isEqualTo(5);

            Review saved = reviewRepository.findById(response.reviewId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
            assertThat(saved.getProduct().getId()).isEqualTo(product.getId());
        }

        @Test
        @DisplayName("타인의 주문에는 리뷰를 작성할 수 없다")
        void fail_notOwner() {
            OrderItem oi = createOrderItemWithStatus(buyer, OrderItemStatus.DELIVERED);

            CreateReviewRequest req = new CreateReviewRequest();
            setField(req, "orderItemId", oi.getId());
            setField(req, "rating", 5);
            setField(req, "content", "타인 주문에 리뷰를 쓰려는 시도");

            assertThatThrownBy(() ->
                reviewService.createReview(authOf(anotherBuyer), req, null)
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.REVIEW_006));
        }

        @Test
        @DisplayName("SHIPPING 상태 주문에는 리뷰를 작성할 수 없다")
        void fail_shipping() {
            OrderItem oi = createOrderItemWithStatus(buyer, OrderItemStatus.SHIPPING);

            CreateReviewRequest req = new CreateReviewRequest();
            setField(req, "orderItemId", oi.getId());
            setField(req, "rating", 5);
            setField(req, "content", "아직 배송중인데 리뷰를 쓰려는 시도");

            assertThatThrownBy(() ->
                reviewService.createReview(authOf(buyer), req, null)
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.REVIEW_001));
        }

        @Test
        @DisplayName("CANCELLED 주문에는 리뷰를 작성할 수 없다")
        void fail_cancelled() {
            OrderItem oi = createOrderItemWithStatus(buyer, OrderItemStatus.ORDERED);
            oi.cancel();
            orderItemRepository.saveAndFlush(oi);

            CreateReviewRequest req = new CreateReviewRequest();
            setField(req, "orderItemId", oi.getId());
            setField(req, "rating", 5);
            setField(req, "content", "취소된 주문에 리뷰를 쓰려는 시도");

            assertThatThrownBy(() ->
                reviewService.createReview(authOf(buyer), req, null)
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.REVIEW_001));
        }

        @Test
        @DisplayName("동일 주문 상품에 중복 리뷰를 작성할 수 없다")
        void fail_duplicate() {
            OrderItem oi = createOrderItemWithStatus(buyer, OrderItemStatus.DELIVERED);

            CreateReviewRequest req = new CreateReviewRequest();
            setField(req, "orderItemId", oi.getId());
            setField(req, "rating", 5);
            setField(req, "content", "첫 번째 리뷰 작성입니다 감사합니다");

            reviewService.createReview(authOf(buyer), req, null);

            // 같은 orderItemId로 재작성 시도
            CreateReviewRequest dupReq = new CreateReviewRequest();
            setField(dupReq, "orderItemId", oi.getId());
            setField(dupReq, "rating", 3);
            setField(dupReq, "content", "두 번째 리뷰 작성 시도입니다 실패해야함");

            assertThatThrownBy(() ->
                reviewService.createReview(authOf(buyer), dupReq, null)
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.REVIEW_002));
        }
    }

    @Nested
    @DisplayName("리뷰 수정·삭제")
    class UpdateAndDelete {

        @Test
        @DisplayName("본인 리뷰를 soft delete할 수 있다")
        void delete_success() {
            OrderItem oi = createOrderItemWithStatus(buyer, OrderItemStatus.DELIVERED);

            CreateReviewRequest req = new CreateReviewRequest();
            setField(req, "orderItemId", oi.getId());
            setField(req, "rating", 5);
            setField(req, "content", "삭제할 리뷰입니다 잘 작성했습니다");

            ReviewResponse created = reviewService.createReview(authOf(buyer), req, null);

            reviewService.deleteReview(authOf(buyer), created.reviewId());

            Review deleted = reviewRepository.findById(created.reviewId()).orElseThrow();
            assertThat(deleted.getStatus()).isEqualTo(ReviewStatus.DELETED);
        }

        @Test
        @DisplayName("타인의 리뷰는 삭제할 수 없다")
        void delete_fail_notOwner() {
            OrderItem oi = createOrderItemWithStatus(buyer, OrderItemStatus.DELIVERED);

            CreateReviewRequest req = new CreateReviewRequest();
            setField(req, "orderItemId", oi.getId());
            setField(req, "rating", 5);
            setField(req, "content", "내 리뷰입니다 다른사람이 삭제시도");

            ReviewResponse created = reviewService.createReview(authOf(buyer), req, null);

            assertThatThrownBy(() ->
                reviewService.deleteReview(authOf(anotherBuyer), created.reviewId())
            )
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.REVIEW_006));

            // 삭제 안 됐는지 확인
            Review stillActive = reviewRepository.findById(created.reviewId()).orElseThrow();
            assertThat(stillActive.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Bean Validation — 별점·내용 길이")
    class BeanValidation {

        @Test
        @DisplayName("별점 0은 @Min(1) 위반이다")
        void rating_zero_rejected() {
            CreateReviewRequest req = new CreateReviewRequest();
            setField(req, "orderItemId", 1L);
            setField(req, "rating", 0);
            setField(req, "content", "별점이 0이면 안됩니다 테스트중입니다");

            Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rating"));
        }

        @Test
        @DisplayName("별점 6은 @Max(5) 위반이다")
        void rating_six_rejected() {
            CreateReviewRequest req = new CreateReviewRequest();
            setField(req, "orderItemId", 1L);
            setField(req, "rating", 6);
            setField(req, "content", "별점이 6이면 안됩니다 테스트중입니다");

            Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rating"));
        }

        @Test
        @DisplayName("별점 1과 5는 허용된다")
        void rating_boundary_accepted() {
            CreateReviewRequest req1 = new CreateReviewRequest();
            setField(req1, "orderItemId", 1L);
            setField(req1, "rating", 1);
            setField(req1, "content", "별점 1점 최소값 경계 테스트입니다");

            CreateReviewRequest req5 = new CreateReviewRequest();
            setField(req5, "orderItemId", 1L);
            setField(req5, "rating", 5);
            setField(req5, "content", "별점 5점 최대값 경계 테스트입니다");

            assertThat(validator.validate(req1)).isEmpty();
            assertThat(validator.validate(req5)).isEmpty();
        }

        @Test
        @DisplayName("내용 9자는 @Size(min=10) 위반이다")
        void content_tooShort_rejected() {
            CreateReviewRequest req = new CreateReviewRequest();
            setField(req, "orderItemId", 1L);
            setField(req, "rating", 5);
            setField(req, "content", "123456789"); // 9자

            Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("content"));
        }

        @Test
        @DisplayName("내용 10자는 허용된다")
        void content_minBoundary_accepted() {
            CreateReviewRequest req = new CreateReviewRequest();
            setField(req, "orderItemId", 1L);
            setField(req, "rating", 5);
            setField(req, "content", "1234567890"); // 정확히 10자

            Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(req);

            assertThat(violations.stream()
                .noneMatch(v -> v.getPropertyPath().toString().equals("content")))
                .isTrue();
        }

        @Test
        @DisplayName("내용 1001자는 @Size(max=1000) 위반이다")
        void content_tooLong_rejected() {
            CreateReviewRequest req = new CreateReviewRequest();
            setField(req, "orderItemId", 1L);
            setField(req, "rating", 5);
            setField(req, "content", "가".repeat(1001));

            Set<ConstraintViolation<CreateReviewRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("content"));
        }
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("필드 설정 실패: " + fieldName, e);
        }
    }
}
