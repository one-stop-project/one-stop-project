package com.sparta.one_stop.domain.order.entity;

import com.sparta.one_stop.domain.coupon.entity.UserCoupon;
import com.sparta.one_stop.domain.subscription.entity.Subscription;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.order.OrderStatus;
import com.sparta.one_stop.global.enums.order.OrderType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_orders_user", columnList = "user_id, created_at"),
        @Index(name = "idx_orders_status", columnList = "status, created_at"),
        @Index(name = "idx_orders_user_coupon", columnList = "user_coupon_id")
    }
)
public class Order extends BaseEntity {

    // 주문 id
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    // 주문자 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 구독 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    // 적용 쿠폰 정보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_coupon_id")
    private UserCoupon userCoupon;

    // 총 주문 금액
    @Column(name = "total_price", nullable = false)
    private Long totalPrice;

    // 총 할인 금액
    @Column(name = "discount_price", nullable = false)
    private Long discountPrice;

    // 최종 결제 금액
    @Column(name = "final_price", nullable = false)
    private Long finalPrice;

    // 사용 포인트
    @Column(name = "used_point", nullable = false)
    private Integer usedPoint;

    // 주문 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    // 수령인 이름
    @Column(name = "receiver_name", nullable = false, length = 50)
    private String receiverName;

    // 수령인 연락처
    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    // 배송 주소
    @Column(name = "receiver_address", nullable = false, length = 255)
    private String receiverAddress;

    // 배송 요청사항
    @Column(name = "delivery_message", length = 50)
    private String deliveryMessage;

    // 배송비
    @Column(name = "delivery_fee", nullable = false)
    private Long deliveryFee;

    // 주문 유입 경로
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    // 구독 할인 금액
    @Column(name = "subscription_discount")
    private Long subscriptionDiscount;

    // == 생성자 ==
    public Order(
        User user,
        UserCoupon userCoupon,
        Long totalPrice,
        Long discountPrice,
        Long finalPrice,
        Integer usedPoint,
        Long subscriptionDiscount,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        String deliveryMessage,
        Long deliveryFee,
        OrderType orderType
    ) {
        validateConstructorArguments(
            user,
            totalPrice,
            discountPrice,
            finalPrice,
            usedPoint,
            subscriptionDiscount,
            receiverName,
            receiverPhone,
            receiverAddress,
            deliveryFee,
            orderType
        );

        this.user = user;
        this.userCoupon = userCoupon;
        this.totalPrice = totalPrice;
        this.discountPrice = discountPrice;
        this.finalPrice = finalPrice;
        this.usedPoint = usedPoint;
        this.subscriptionDiscount = subscriptionDiscount != null ? subscriptionDiscount : 0L;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.receiverAddress = receiverAddress;
        this.deliveryMessage = deliveryMessage;
        this.deliveryFee = deliveryFee;
        this.orderType = orderType;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    // == 검증 메서드 ==

    private void validateConstructorArguments(
        User user,
        Long totalPrice,
        Long discountPrice,
        Long finalPrice,
        Integer usedPoint,
        Long subscriptionDiscount,
        String receiverName,
        String receiverPhone,
        String receiverAddress,
        Long deliveryFee,
        OrderType orderType
    ) {
        if (user == null) {
            throw new CustomException(ErrorCode.ORDER_039);
        }

        if (totalPrice == null || totalPrice < 0) {
            throw new CustomException(ErrorCode.ORDER_040);
        }

        if (discountPrice == null || discountPrice < 0) {
            throw new CustomException(ErrorCode.ORDER_041);
        }

        if (finalPrice == null || finalPrice < 0) {
            throw new CustomException(ErrorCode.ORDER_042);
        }

        if (usedPoint == null || usedPoint < 0) {
            throw new CustomException(ErrorCode.ORDER_043);
        }

        if (subscriptionDiscount != null && subscriptionDiscount < 0) {
            throw new CustomException(ErrorCode.ORDER_044);
        }

        if (receiverName == null || receiverName.isBlank()) {
            throw new CustomException(ErrorCode.ORDER_045);
        }

        if (receiverPhone == null || receiverPhone.isBlank()) {
            throw new CustomException(ErrorCode.ORDER_046);
        }

        if (receiverAddress == null || receiverAddress.isBlank()) {
            throw new CustomException(ErrorCode.ORDER_047);
        }

        if (deliveryFee == null || deliveryFee < 0) {
            throw new CustomException(ErrorCode.ORDER_048);
        }

        if (orderType == null) {
            throw new CustomException(ErrorCode.ORDER_049);
        }
    }

    // == 비즈니스 메서드 ==

    // 결제 완료
    public void completePayment() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new CustomException(ErrorCode.ORDER_050);
        }

        this.status = OrderStatus.PAID;
    }

    // 주문 취소
    public void cancel() {
        if (this.status == OrderStatus.CANCELLED) {
            throw new CustomException(ErrorCode.ORDER_051);
        }

        this.status = OrderStatus.CANCELLED;
    }

}
